// Telegram Bot API: publicação automática oficial em canais e grupos
// onde o bot foi adicionado como administrador.
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { fetchRetry } from "../lib/http.js";
import { IntegracaoErro, type StatusIntegracao } from "./types.js";

export interface FotoEnvio {
  tipo: string;
  base64: string;
}

export interface Publicacao {
  chave: string;
  chatId: string;
  messageId: number;
  publicadoEm: string;
}

export class Telegram {
  private enviados = new Map<string, Publicacao>();
  private carregado = false;

  constructor(
    private readonly opts: { token?: string; dataDir: string },
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  get configurada() {
    return Boolean(this.opts.token);
  }

  private get arquivo() {
    return join(this.opts.dataDir, "telegram-publicacoes.json");
  }

  private async carregar() {
    if (this.carregado) return;
    try {
      const lista = JSON.parse(await readFile(this.arquivo, "utf8")) as Publicacao[];
      for (const p of lista) this.enviados.set(p.chave, p);
    } catch {
      /* primeiro uso */
    }
    this.carregado = true;
  }

  private async salvar() {
    await mkdir(this.opts.dataDir, { recursive: true });
    const lista = [...this.enviados.values()].slice(-2000);
    await writeFile(this.arquivo, JSON.stringify(lista));
  }

  private async api<T>(metodo: string, corpo: Record<string, unknown> | FormData): Promise<T> {
    if (!this.opts.token) throw new IntegracaoErro("nao_configurado", "Telegram não configurado (TELEGRAM_BOT_TOKEN)", 503);
    // Sem repetição automática no envio: uma repetição poderia duplicar a postagem.
    const res = await fetchRetry(`https://api.telegram.org/bot${this.opts.token}/${metodo}`, {
      method: "POST",
      ...(corpo instanceof FormData ? { body: corpo } : { headers: { "content-type": "application/json" }, body: JSON.stringify(corpo) }),
      timeoutMs: 15_000,
      retries: metodo.startsWith("get") ? 2 : 0,
      fetchImpl: this.fetchImpl,
    });
    const j = (await res.json().catch(() => ({}))) as { ok?: boolean; result?: T; description?: string; error_code?: number; parameters?: { retry_after?: number } };
    if (j.ok && j.result !== undefined) return j.result;
    const d = j.description ?? `HTTP ${res.status}`;
    if (res.status === 401) throw new IntegracaoErro("autenticacao", "Token do bot do Telegram inválido", 401);
    if (res.status === 429) throw new IntegracaoErro("limite", `Telegram pediu para esperar ${j.parameters?.retry_after ?? "alguns"} segundos`, 429);
    if (res.status === 403 || res.status === 400) throw new IntegracaoErro("sem_acesso", `Telegram recusou: ${d}`, 400);
    throw new IntegracaoErro("indisponivel", `Telegram: ${d}`);
  }

  /**
   * Publica uma vez por chave. Se a mesma chave chegar de novo (reenvio após
   * queda de conexão), devolve a publicação anterior sem postar de novo.
   */
  async publicar(p: {
    chave: string;
    chatId: string;
    texto: string;
    imagem?: string | null;
    foto?: FotoEnvio | null;
    fotos?: FotoEnvio[] | null;
    /** "html": o texto vem com <b>, <s>, <i> (convertido do formato do WhatsApp). */
    formato?: "html" | null;
  }) {
    await this.carregar();
    const anterior = this.enviados.get(p.chave);
    if (anterior) return { ...anterior, repetido: true };
    type Msg = { message_id: number; chat: { id: number } };
    // Nunca cortar o texto: o link de afiliado fica no fim e se perderia.
    if (p.texto.length > 4096) throw new IntegracaoErro("resposta_invalida", "Texto maior que 4096 caracteres. Encurte antes de publicar.", 400);
    const cabe = p.texto.length <= 1024;
    const fotos = [...(p.fotos ?? []), ...(p.foto ? [p.foto] : [])].slice(0, 10);
    const arquivo = (f: FotoEnvio) => new Blob([Buffer.from(f.base64, "base64")], { type: f.tipo });
    const pm = p.formato === "html" ? { parse_mode: "HTML" } : {};
    let msg: Msg;
    if (fotos.length > 1) {
      // Álbum: o texto vai na legenda da primeira foto (ou em mensagem própria, se for longo).
      const form = new FormData();
      form.append("chat_id", p.chatId);
      form.append("media", JSON.stringify(fotos.map((_, i) => ({ type: "photo", media: `attach://foto${i}`, ...(i === 0 && cabe ? { caption: p.texto, ...pm } : {}) }))));
      fotos.forEach((f, i) => form.append(`foto${i}`, arquivo(f), `foto${i}.jpg`));
      const album = await this.api<Msg[]>("sendMediaGroup", form);
      msg = cabe ? album[0]! : await this.api<Msg>("sendMessage", { chat_id: p.chatId, text: p.texto, ...pm });
    } else if (fotos.length === 1 && cabe) {
      const form = new FormData();
      form.append("chat_id", p.chatId);
      form.append("caption", p.texto);
      if (p.formato === "html") form.append("parse_mode", "HTML");
      form.append("photo", arquivo(fotos[0]!), "produto.jpg");
      msg = await this.api<Msg>("sendPhoto", form);
    } else if (p.imagem && cabe) {
      msg = await this.api<Msg>("sendPhoto", { chat_id: p.chatId, photo: p.imagem, caption: p.texto, ...pm });
    } else {
      msg = await this.api<Msg>("sendMessage", { chat_id: p.chatId, text: p.texto, link_preview_options: { is_disabled: false }, ...pm });
    }
    const pub: Publicacao = { chave: p.chave, chatId: String(msg.chat.id), messageId: msg.message_id, publicadoEm: new Date().toISOString() };
    this.enviados.set(p.chave, pub);
    await this.salvar();
    return { ...pub, repetido: false };
  }

  async testarDestino(chatId: string) {
    const chat = await this.api<{ id: number; title?: string; type: string }>("getChat", { chat_id: chatId });
    return { id: String(chat.id), titulo: chat.title ?? null, tipo: chat.type };
  }

  async status(testar: boolean): Promise<StatusIntegracao> {
    const base = {
      id: "telegram" as const,
      nome: "Telegram (canais e grupos)",
      verificadoEm: new Date().toISOString(),
      comoConfigurar:
        "1) No Telegram, fale com @BotFather e crie um bot. 2) Coloque o token em TELEGRAM_BOT_TOKEN. 3) Adicione o bot como administrador do seu canal ou grupo. 4) No app, cadastre o canal com @nome_do_canal ou o ID do grupo.",
    };
    const ops = (ok: boolean, motivo?: string) => [{ id: "publicar", descricao: "Publicar automaticamente com confirmação", implementada: true, disponivel: ok, motivo }];
    if (!this.configurada) return { ...base, estado: "nao_configurado", detalhe: "Falta TELEGRAM_BOT_TOKEN", operacoes: ops(false, "Não configurado") };
    if (!testar) return { ...base, estado: "configurado", detalhe: "Token presente, ainda não testado", operacoes: ops(true) };
    try {
      const me = await this.api<{ username: string }>("getMe", {});
      return { ...base, estado: "configurado", detalhe: `Bot @${me.username} conectado`, operacoes: ops(true) };
    } catch (e) {
      const err = e as IntegracaoErro;
      return { ...base, estado: err.codigo === "autenticacao" ? "erro_autenticacao" : "indisponivel", detalhe: err.message, operacoes: ops(false, err.message) };
    }
  }
}

export function statusWhatsapp(): StatusIntegracao {
  return {
    id: "whatsapp",
    nome: "WhatsApp",
    estado: "indisponivel",
    detalhe:
      "Envio automático para grupos comuns não é permitido pela via oficial. A API de Grupos da plataforma Business exige conta oficial (OBA) e grupos criados pela própria empresa. Use o compartilhamento do Android.",
    verificadoEm: new Date().toISOString(),
    comoConfigurar: "Nada a configurar no backend. No app, toque em Compartilhar e escolha o grupo ou canal. Depois marque como publicado.",
    operacoes: [
      { id: "compartilhar", descricao: "Compartilhar pelo Android (manual)", implementada: true, disponivel: true, motivo: "Feito no app; o envio é confirmado por você" },
      { id: "envio_automatico_grupo", descricao: "Postar sozinho em grupos", implementada: false, disponivel: false, motivo: "Sem via oficial para grupos comuns" },
    ],
  };
}
