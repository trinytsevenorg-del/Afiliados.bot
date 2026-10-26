// WhatsApp automático por um número próprio (chip separado), via Baileys.
// NÃO é a API oficial: o WhatsApp pode banir o número. Por isso:
// só posta em grupos escolhidos, nunca no privado; intervalo mínimo e
// limite diário por grupo; "digitando…" antes de cada envio.
import makeWASocket, {
  Browsers,
  BufferJSON,
  DisconnectReason,
  fetchLatestWaWebVersion,
  initAuthCreds,
  makeCacheableSignalKeyStore,
  proto,
  type AuthenticationCreds,
  type SignalDataTypeMap,
  type WASocket,
} from "baileys";
import pino from "pino";
import { IntegracaoErro, type StatusIntegracao } from "./types.js";

/** Onde a sessão e o histórico de envios ficam (Postgres em produção, memória nos testes). */
export interface Armazem {
  ler(chave: string): Promise<string | null>;
  gravar(chave: string, valor: string): Promise<void>;
  apagar(chave: string): Promise<void>;
  apagarSessao(): Promise<void>;
  envio(chave: string): Promise<{ grupo: string; mensagemId: string; quando: number } | null>;
  registrarEnvio(chave: string, grupo: string, mensagemId: string, quando: number): Promise<void>;
  enviosDoGrupo(grupo: string, desde: number): Promise<number[]>;
}

export class ArmazemMemoria implements Armazem {
  sessao = new Map<string, string>();
  envios = new Map<string, { grupo: string; mensagemId: string; quando: number }>();
  async ler(c: string) { return this.sessao.get(c) ?? null; }
  async gravar(c: string, v: string) { this.sessao.set(c, v); }
  async apagar(c: string) { this.sessao.delete(c); }
  async apagarSessao() { this.sessao.clear(); }
  async envio(c: string) { return this.envios.get(c) ?? null; }
  async registrarEnvio(c: string, grupo: string, mensagemId: string, quando: number) { this.envios.set(c, { grupo, mensagemId, quando }); }
  async enviosDoGrupo(grupo: string, desde: number) {
    return [...this.envios.values()].filter((e) => e.grupo === grupo && e.quando >= desde).map((e) => e.quando);
  }
}

export class ArmazemPostgres implements Armazem {
  private pronto: Promise<void>;
  constructor(private readonly pool: import("pg").Pool) {
    this.pronto = pool
      .query(
        `CREATE TABLE IF NOT EXISTS wa_sessao (chave text PRIMARY KEY, valor text NOT NULL);
         CREATE TABLE IF NOT EXISTS wa_envios (chave text PRIMARY KEY, grupo text NOT NULL, mensagem_id text NOT NULL, quando bigint NOT NULL);
         CREATE INDEX IF NOT EXISTS wa_envios_grupo ON wa_envios (grupo, quando);`,
      )
      .then(() => undefined);
  }
  private async q(sql: string, p: unknown[] = []) {
    await this.pronto;
    return this.pool.query(sql, p);
  }
  async ler(c: string) { return ((await this.q("SELECT valor FROM wa_sessao WHERE chave = $1", [c])).rows[0]?.valor as string) ?? null; }
  async gravar(c: string, v: string) { await this.q("INSERT INTO wa_sessao (chave, valor) VALUES ($1, $2) ON CONFLICT (chave) DO UPDATE SET valor = EXCLUDED.valor", [c, v]); }
  async apagar(c: string) { await this.q("DELETE FROM wa_sessao WHERE chave = $1", [c]); }
  async apagarSessao() { await this.q("DELETE FROM wa_sessao"); }
  async envio(c: string) {
    const r = (await this.q("SELECT grupo, mensagem_id, quando FROM wa_envios WHERE chave = $1", [c])).rows[0];
    return r ? { grupo: r.grupo as string, mensagemId: r.mensagem_id as string, quando: Number(r.quando) } : null;
  }
  async registrarEnvio(c: string, grupo: string, mensagemId: string, quando: number) {
    await this.q("INSERT INTO wa_envios (chave, grupo, mensagem_id, quando) VALUES ($1, $2, $3, $4) ON CONFLICT DO NOTHING", [c, grupo, mensagemId, quando]);
  }
  async enviosDoGrupo(grupo: string, desde: number) {
    return (await this.q("SELECT quando FROM wa_envios WHERE grupo = $1 AND quando >= $2", [grupo, desde])).rows.map((r) => Number(r.quando));
  }
}

/** Sessão do Baileys guardada no Armazem (mesmo formato do useMultiFileAuthState). */
async function sessaoNoArmazem(a: Armazem) {
  const ler = async (k: string) => {
    const v = await a.ler(k);
    return v ? JSON.parse(v, BufferJSON.reviver) : null;
  };
  const gravar = (k: string, v: unknown) => a.gravar(k, JSON.stringify(v, BufferJSON.replacer));
  const creds: AuthenticationCreds = (await ler("creds")) ?? initAuthCreds();
  return {
    state: {
      creds,
      keys: {
        get: async <T extends keyof SignalDataTypeMap>(tipo: T, ids: string[]) => {
          const out: { [id: string]: SignalDataTypeMap[T] } = {};
          await Promise.all(ids.map(async (id) => {
            let v = await ler(`${tipo}-${id}`);
            if (tipo === "app-state-sync-key" && v) v = proto.Message.AppStateSyncKeyData.fromObject(v);
            out[id] = v;
          }));
          return out;
        },
        set: async (data: Record<string, Record<string, unknown>>) => {
          const tarefas: Promise<void>[] = [];
          for (const tipo in data) for (const id in data[tipo]) {
            const v = data[tipo]![id];
            tarefas.push(v ? gravar(`${tipo}-${id}`, v) : a.apagar(`${tipo}-${id}`));
          }
          await Promise.all(tarefas);
        },
      },
    },
    salvarCreds: () => gravar("creds", creds),
  };
}

export type EstadoWa = "nao_configurado" | "desconectado" | "conectando" | "aguardando_codigo" | "conectado";

export interface LimitesWa {
  /** Minutos mínimos entre duas mensagens no mesmo grupo. */
  intervaloMin: number;
  /** Máximo de mensagens por grupo a cada 24 h. */
  maxDia: number;
}

type CriarSocket = (opts: Parameters<typeof makeWASocket>[0]) => WASocket;

export class WhatsApp {
  private sock: WASocket | null = null;
  estado: EstadoWa;
  numero: string | null = null;
  ultimoErro: string | null = null;
  private codigo: string | null = null;
  private tentativas = 0;
  private parando = false;

  constructor(
    private readonly armazem: Armazem | null,
    private readonly limites: LimitesWa,
    private readonly criarSocket: CriarSocket = makeWASocket,
    private readonly espera: (ms: number) => Promise<void> = (ms) => new Promise((r) => setTimeout(r, ms)),
    private readonly agora: () => number = Date.now,
  ) {
    this.estado = armazem ? "desconectado" : "nao_configurado";
  }

  private exigirArmazem() {
    if (!this.armazem) throw new IntegracaoErro("nao_configurado", "WhatsApp automático não configurado (DATABASE_URL)", 503);
    return this.armazem;
  }

  /** Ao ligar o backend: reconecta se já existe sessão salva. */
  async iniciar() {
    if (!this.armazem) return;
    if (await this.armazem.ler("creds")) await this.abrir().catch((e) => { this.ultimoErro = String(e?.message ?? e); });
  }

  private async abrir(telefone?: string): Promise<string | null> {
    const a = this.exigirArmazem();
    this.parando = false;
    const { state, salvarCreds } = await sessaoNoArmazem(a);
    const logger = pino({ level: "silent" });
    // O WhatsApp recusa versões velhas (erro 405): pega a versão atual do WhatsApp Web.
    const versao = this.criarSocket === makeWASocket
      ? await fetchLatestWaWebVersion({ timeout: 10_000 }).then((r) => r.version).catch(() => undefined)
      : undefined;
    this.estado = "conectando";
    this.codigo = null;
    const sock = this.criarSocket({
      auth: { creds: state.creds, keys: makeCacheableSignalKeyStore(state.keys as never, logger) },
      logger,
      browser: Browsers.macOS("Chrome"),
      markOnlineOnConnect: false,
      syncFullHistory: false,
      printQRInTerminal: false,
      ...(versao ? { version: versao } : {}),
    });
    this.sock = sock;
    sock.ev.on("creds.update", salvarCreds);

    let resolverCodigo: (c: string | null) => void = () => {};
    const codigo = new Promise<string | null>((r) => (resolverCodigo = r));

    sock.ev.on("connection.update", async (u) => {
      if (u.qr && !state.creds.registered && telefone && !this.codigo) {
        try {
          this.codigo = await sock.requestPairingCode(telefone);
          this.estado = "aguardando_codigo";
          resolverCodigo(this.codigo);
        } catch (e) {
          this.ultimoErro = `Não deu para gerar o código: ${(e as Error).message}`;
          resolverCodigo(null);
        }
      }
      if (u.connection === "open") {
        this.estado = "conectado";
        this.tentativas = 0;
        this.ultimoErro = null;
        this.numero = sock.user?.id?.split(":")[0]?.split("@")[0] ?? null;
        resolverCodigo(null);
      }
      if (u.connection === "close") {
        const status = (u.lastDisconnect?.error as { output?: { statusCode?: number } } | undefined)?.output?.statusCode;
        this.sock = null;
        if (status === DisconnectReason.loggedOut) {
          this.estado = "desconectado";
          this.numero = null;
          this.ultimoErro = "O WhatsApp desconectou este aparelho. Conecte de novo.";
          await a.apagarSessao();
        } else if (!this.parando && state.creds.registered) {
          // Queda de rede ou reinício do WhatsApp: tenta de novo, cada vez mais devagar.
          this.estado = "conectando";
          const atraso = Math.min(60_000, 3_000 * 2 ** this.tentativas++);
          await this.espera(atraso);
          if (!this.parando) this.abrir().catch(() => {});
        } else {
          this.estado = "desconectado";
        }
        resolverCodigo(null);
      }
    });
    return telefone ? codigo : null;
  }

  /** Gera o código de 8 letras para digitar no WhatsApp do chip. */
  async conectar(telefone: string) {
    const num = telefone.replace(/\D/g, "");
    if (!/^55\d{10,11}$/.test(num)) throw new IntegracaoErro("resposta_invalida", "Telefone no formato 55 + DDD + número (ex.: 5521999998888)", 400);
    const a = this.exigirArmazem();
    if (this.estado === "conectado") throw new IntegracaoErro("resposta_invalida", "Já está conectado. Desconecte antes de trocar de número.", 400);
    this.parando = true;
    this.sock?.end(undefined);
    await a.apagarSessao();
    const tempo = new Promise<null>((r) => setTimeout(() => r(null), 30_000).unref());
    const codigo = await Promise.race([this.abrir(num), tempo]);
    if (!codigo) throw new IntegracaoErro("indisponivel", this.ultimoErro ?? "O WhatsApp não respondeu. Tente de novo em 1 minuto.", 504);
    return { codigo: codigo.match(/.{1,4}/g)!.join("-") };
  }

  async desconectar() {
    const a = this.exigirArmazem();
    this.parando = true;
    try { await this.sock?.logout(); } catch { /* já caiu */ }
    this.sock = null;
    await a.apagarSessao();
    this.estado = "desconectado";
    this.numero = null;
  }

  private exigirConectado() {
    if (!this.sock || this.estado !== "conectado") throw new IntegracaoErro("indisponivel", "WhatsApp não está conectado. Abra Ajustes → WhatsApp automático.", 503);
    return this.sock;
  }

  async grupos() {
    const sock = this.exigirConectado();
    const todos = await sock.groupFetchAllParticipating();
    const eu = sock.user?.id?.split(":")[0];
    return Object.values(todos)
      .map((g) => ({
        id: g.id,
        nome: g.subject,
        participantes: g.participants.length,
        souAdmin: g.participants.some((p) => p.id.split(":")[0]?.split("@")[0] === eu?.split("@")[0] && !!p.admin),
        soAdminsEnviam: !!g.announce,
      }))
      .sort((x, y) => x.nome.localeCompare(y.nome));
  }

  async publicar(p: { chave: string; grupo: string; texto: string; foto?: { tipo: string; base64: string } | null }) {
    const a = this.exigirArmazem();
    const anterior = await a.envio(p.chave);
    if (anterior) return { ...anterior, repetido: true };
    if (!p.grupo.endsWith("@g.us")) throw new IntegracaoErro("resposta_invalida", "Só é permitido postar em grupos", 400);
    const sock = this.exigirConectado();

    const agora = this.agora();
    const recentes = await a.enviosDoGrupo(p.grupo, agora - 24 * 3_600_000);
    const ultimo = Math.max(0, ...recentes);
    const falta = ultimo + this.limites.intervaloMin * 60_000 - agora;
    if (falta > 0) throw new IntegracaoErro("limite", `Espere ${Math.ceil(falta / 60_000)} min para postar de novo neste grupo (intervalo mínimo de ${this.limites.intervaloMin} min).`, 429);
    if (recentes.length >= this.limites.maxDia) throw new IntegracaoErro("limite", `Limite de ${this.limites.maxDia} mensagens por dia neste grupo atingido.`, 429);

    const meta = await sock.groupMetadata(p.grupo).catch(() => null);
    if (!meta) throw new IntegracaoErro("sem_acesso", "Este número não está nesse grupo", 400);

    // Ritmo de pessoa: "digitando…" por alguns segundos antes de mandar.
    await sock.sendPresenceUpdate("composing", p.grupo).catch(() => {});
    await this.espera(2_000 + Math.floor(Math.random() * 4_000));
    await sock.sendPresenceUpdate("paused", p.grupo).catch(() => {});

    const msg = p.foto
      ? await sock.sendMessage(p.grupo, { image: Buffer.from(p.foto.base64, "base64"), caption: p.texto })
      : await sock.sendMessage(p.grupo, { text: p.texto });
    const id = msg?.key?.id;
    if (!id) throw new IntegracaoErro("indisponivel", "O WhatsApp não confirmou o envio", 502);
    await a.registrarEnvio(p.chave, p.grupo, id, this.agora());
    return { grupo: p.grupo, mensagemId: id, quando: this.agora(), repetido: false };
  }

  status(): StatusIntegracao {
    const ops = (ok: boolean, motivo?: string) => [
      { id: "compartilhar", descricao: "Compartilhar pelo Android (manual)", implementada: true, disponivel: true },
      { id: "envio_automatico_grupo", descricao: "Postar sozinho nos grupos escolhidos (número próprio, não oficial)", implementada: true, disponivel: ok, motivo },
    ];
    const base = {
      id: "whatsapp" as const,
      nome: "WhatsApp",
      verificadoEm: new Date().toISOString(),
      comoConfigurar:
        "1) Crie um projeto grátis no Supabase e coloque a connection string (Session pooler) em DATABASE_URL no Render. 2) No app: Ajustes → WhatsApp automático → digite o número do chip → digite o código no WhatsApp desse chip (Aparelhos conectados → Conectar com número de telefone).",
    };
    if (this.estado === "nao_configurado") return { ...base, estado: "nao_configurado", detalhe: "Falta DATABASE_URL. Enquanto isso, use o compartilhamento.", operacoes: ops(false, "Não configurado") };
    if (this.estado === "conectado") return { ...base, estado: "configurado", detalhe: `Conectado no número ${this.numero ?? "?"}. Não oficial: risco de banimento do chip.`, operacoes: ops(true) };
    return { ...base, estado: "aguardando_autorizacao", detalhe: this.ultimoErro ?? (this.estado === "aguardando_codigo" ? "Digite o código no WhatsApp do chip" : "Número não conectado"), operacoes: ops(false, "Não conectado") };
  }

  /** Para o app: estado detalhado. */
  resumo() {
    return { estado: this.estado, numero: this.numero, erro: this.ultimoErro, limites: this.limites };
  }
}
