import { IntegracaoErro } from "./integrations/types.js";
import type { Telegram } from "./integrations/telegram.js";
import type { WhatsApp } from "./integrations/whatsapp.js";

/**
 * Fila no servidor: o app manda as ofertas agendadas e o servidor posta na hora,
 * mesmo com o celular desligado. A chave de cada item é a mesma do app, então
 * reenviar ou repetir nunca duplica a mensagem (Telegram e WhatsApp já conferem a chave).
 */

export type Foto = { tipo: "image/jpeg" | "image/png" | "image/webp"; base64: string };

export interface PedidoFila {
  chave: string;
  canal: "telegram" | "whatsapp";
  destino: string;
  texto: string;
  fotos?: Foto[] | null;
  imagem?: string | null;
  /** Quando postar (ms). */
  quando: number;
  /** Depois disso o preço não vale mais: não posta (ms). */
  validoAte?: number | null;
}

export type EstadoItem = "agendado" | "enviando" | "publicado" | "falha" | "cancelado";

export interface ItemServidor extends PedidoFila {
  estado: EstadoItem;
  resultado: string | null;
  mensagemId: string | null;
  tentativas: number;
  criadoEm: number;
  atualizadoEm: number;
}

/** O que o app recebe ao consultar (sem fotos nem texto, para a resposta ser leve). */
export type SituacaoItem = Pick<ItemServidor, "chave" | "estado" | "resultado" | "mensagemId" | "quando" | "atualizadoEm">;

export interface ArmazemFila {
  salvar(item: ItemServidor): Promise<void>;
  pegar(chave: string): Promise<ItemServidor | null>;
  vencidos(agora: number, limite: number): Promise<ItemServidor[]>;
  /** Itens que ficaram "enviando" (servidor reiniciou no meio). */
  presos(antesDe: number): Promise<ItemServidor[]>;
  /** Apaga itens finalizados mais velhos que [antesDe]. */
  limpar(antesDe: number): Promise<void>;
}

export class ArmazemFilaMemoria implements ArmazemFila {
  itens = new Map<string, ItemServidor>();
  async salvar(i: ItemServidor) { this.itens.set(i.chave, structuredClone(i)); }
  async pegar(c: string) { const i = this.itens.get(c); return i ? structuredClone(i) : null; }
  async vencidos(agora: number, limite: number) {
    return [...this.itens.values()].filter((i) => i.estado === "agendado" && i.quando <= agora).sort((a, b) => a.quando - b.quando).slice(0, limite).map((i) => structuredClone(i));
  }
  async presos(antesDe: number) {
    return [...this.itens.values()].filter((i) => i.estado === "enviando" && i.atualizadoEm < antesDe).map((i) => structuredClone(i));
  }
  async limpar(antesDe: number) {
    for (const [c, i] of this.itens) if (i.estado !== "agendado" && i.estado !== "enviando" && i.atualizadoEm < antesDe) this.itens.delete(c);
  }
}

export class ArmazemFilaPostgres implements ArmazemFila {
  private pronto: Promise<void> | null = null;
  constructor(private readonly pool: import("pg").Pool) {}
  private preparar() {
    this.pronto ??= this.pool
      .query(
        `CREATE TABLE IF NOT EXISTS fila_servidor (chave text PRIMARY KEY, dados jsonb NOT NULL, estado text NOT NULL, quando bigint NOT NULL, atualizado bigint NOT NULL);
         CREATE INDEX IF NOT EXISTS fila_servidor_vencidos ON fila_servidor (estado, quando);`,
      )
      .then(() => undefined, (e) => { this.pronto = null; throw e; });
    return this.pronto;
  }
  private async q(sql: string, p: unknown[] = []) {
    await this.preparar();
    return this.pool.query(sql, p);
  }
  async salvar(i: ItemServidor) {
    await this.q(
      `INSERT INTO fila_servidor (chave, dados, estado, quando, atualizado) VALUES ($1, $2, $3, $4, $5)
       ON CONFLICT (chave) DO UPDATE SET dados = $2, estado = $3, quando = $4, atualizado = $5`,
      [i.chave, JSON.stringify(i), i.estado, i.quando, i.atualizadoEm],
    );
  }
  async pegar(c: string) {
    return ((await this.q("SELECT dados FROM fila_servidor WHERE chave = $1", [c])).rows[0]?.dados as ItemServidor) ?? null;
  }
  async vencidos(agora: number, limite: number) {
    return (await this.q("SELECT dados FROM fila_servidor WHERE estado = 'agendado' AND quando <= $1 ORDER BY quando LIMIT $2", [agora, limite])).rows.map((r) => r.dados as ItemServidor);
  }
  async presos(antesDe: number) {
    return (await this.q("SELECT dados FROM fila_servidor WHERE estado = 'enviando' AND atualizado < $1", [antesDe])).rows.map((r) => r.dados as ItemServidor);
  }
  async limpar(antesDe: number) {
    await this.q("DELETE FROM fila_servidor WHERE estado NOT IN ('agendado', 'enviando') AND atualizado < $1", [antesDe]);
  }
}

const MIN = 60_000;
/** Erros passageiros (WhatsApp caiu, serviço fora): tenta de novo algumas vezes antes de desistir. */
const MAX_TENTATIVAS = 6;

export class FilaServidor {
  private rodando = false;
  constructor(
    private readonly armazem: ArmazemFila | null,
    private readonly servicos: { telegram: Pick<Telegram, "publicar">; whatsapp: Pick<WhatsApp, "publicar"> },
    private readonly agora: () => number = Date.now,
  ) {}

  get ativa() { return this.armazem !== null; }

  private exigir() {
    if (!this.armazem) throw new IntegracaoErro("nao_configurado", "Fila no servidor precisa do banco (DATABASE_URL)", 503);
    return this.armazem;
  }

  /** Agenda (ou reagenda) um item. Item já publicado não muda. */
  async agendar(p: PedidoFila): Promise<SituacaoItem> {
    const a = this.exigir();
    const atual = await a.pegar(p.chave);
    if (atual && (atual.estado === "publicado" || atual.estado === "enviando")) return situacao(atual);
    const agora = this.agora();
    const item: ItemServidor = { ...p, estado: "agendado", resultado: null, mensagemId: null, tentativas: 0, criadoEm: atual?.criadoEm ?? agora, atualizadoEm: agora };
    await a.salvar(item);
    return situacao(item);
  }

  async consultar(chaves: string[]): Promise<SituacaoItem[]> {
    const a = this.exigir();
    const itens = await Promise.all(chaves.map((c) => a.pegar(c)));
    return itens.filter((i): i is ItemServidor => i !== null).map(situacao);
  }

  /** Cancela se ainda não saiu. Devolve a situação real (pode já ter sido publicado). */
  async cancelar(chave: string): Promise<SituacaoItem | null> {
    const a = this.exigir();
    const i = await a.pegar(chave);
    if (!i) return null;
    if (i.estado !== "agendado" && i.estado !== "falha") return situacao(i);
    const novo = { ...i, estado: "cancelado" as const, resultado: "Cancelado pelo app", fotos: null, atualizadoEm: this.agora() };
    await a.salvar(novo);
    return situacao(novo);
  }

  /** Posta o que já venceu. Roda a cada 30 s pelo [iniciar]. */
  async rodar(): Promise<number> {
    if (!this.armazem || this.rodando) return 0;
    this.rodando = true;
    try {
      const a = this.armazem;
      // Nunca reenviar às cegas: pode ter saído antes de o servidor cair.
      for (const i of await a.presos(this.agora() - 5 * MIN)) {
        await a.salvar({ ...i, estado: "falha", resultado: "Envio não confirmado (servidor reiniciou). Confira o canal antes de agendar de novo.", atualizadoEm: this.agora() });
      }
      const lote = await a.vencidos(this.agora(), 5);
      for (const item of lote) await this.enviar(a, item);
      await a.limpar(this.agora() - 30 * 24 * 60 * MIN);
      return lote.length;
    } finally {
      this.rodando = false;
    }
  }

  private async enviar(a: ArmazemFila, item: ItemServidor) {
    const agora = this.agora();
    const fim = (estado: EstadoItem, resultado: string, mensagemId: string | null = null) =>
      a.salvar({ ...item, estado, resultado, mensagemId, fotos: estado === "publicado" ? null : item.fotos, tentativas: item.tentativas + 1, atualizadoEm: this.agora() });
    if (item.validoAte && agora > item.validoAte) {
      return fim("falha", "O preço passou da validade antes de sair. Confira o preço e agende de novo.");
    }
    await a.salvar({ ...item, estado: "enviando", atualizadoEm: agora });
    try {
      if (item.canal === "telegram") {
        const r = await this.servicos.telegram.publicar({ chave: item.chave, chatId: item.destino, texto: item.texto, imagem: item.imagem ?? null, fotos: item.fotos ?? null, formato: "html" });
        await fim("publicado", "Telegram confirmou a mensagem" + (r.repetido ? " (já tinha saído antes)" : ""), String(r.messageId));
      } else {
        const r = await this.servicos.whatsapp.publicar({ chave: item.chave, grupo: item.destino, texto: item.texto, foto: item.fotos?.[0] ?? null });
        await fim("publicado", "WhatsApp confirmou a mensagem" + ("repetido" in r && r.repetido ? " (já tinha saído antes)" : ""), r.mensagemId);
      }
    } catch (e) {
      const erro = e instanceof IntegracaoErro ? e : null;
      const msg = (e as Error)?.message ?? String(e);
      // Limite do WhatsApp (intervalo/dia): não é falha, só espera.
      if (erro?.codigo === "limite") {
        return a.salvar({ ...item, estado: "agendado", quando: agora + 10 * MIN, resultado: `Esperando: ${msg}`, atualizadoEm: this.agora() });
      }
      const passageiro = !erro || erro.codigo === "indisponivel" || erro.codigo === "nao_configurado" || erro.httpStatus >= 500;
      if (passageiro && item.tentativas + 1 < MAX_TENTATIVAS) {
        return a.salvar({ ...item, estado: "agendado", quando: agora + 5 * MIN, tentativas: item.tentativas + 1, resultado: `Tentando de novo: ${msg}`, atualizadoEm: this.agora() });
      }
      await fim("falha", msg);
    }
  }

  iniciar(intervaloMs = 30_000) {
    if (!this.armazem) return null;
    const t = setInterval(() => { void this.rodar().catch(() => undefined); }, intervaloMs);
    t.unref();
    return t;
  }
}

function situacao(i: ItemServidor): SituacaoItem {
  return { chave: i.chave, estado: i.estado, resultado: i.resultado, mensagemId: i.mensagemId, quando: i.quando, atualizadoEm: i.atualizadoEm };
}
