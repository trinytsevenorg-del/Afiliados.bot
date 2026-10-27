import { createHmac, createPrivateKey, createPublicKey, randomInt, sign, timingSafeEqual, type KeyObject } from "node:crypto";
import { IntegracaoErro } from "./integrations/types.js";

/**
 * Licenças de venda (só no servidor central do dono, com LICENCA_CHAVE_PRIVADA).
 * - Essencial: pagamento único, sem atualizações (usa a versão da compra para sempre).
 * - Pro: pagamento único, 6 meses de atualizações.
 * - Premium: assinatura mensal, atualizações enquanto estiver paga.
 * O app recebe um token assinado (ECDSA P-256) e confere com a chave pública, mesmo sem internet.
 */

export type Plano = "essencial" | "pro" | "premium";

export const PLANOS: Record<Plano, { nome: string; aparelhos: number; mesesAtualizacao: number | null; mensal: boolean }> = {
  essencial: { nome: "Essencial", aparelhos: 1, mesesAtualizacao: 0, mensal: false },
  pro: { nome: "Pro", aparelhos: 2, mesesAtualizacao: 6, mensal: false },
  premium: { nome: "Premium", aparelhos: 3, mesesAtualizacao: null, mensal: true },
};

const DIA = 86_400_000;
/** Assinatura atrasada continua funcionando por alguns dias. */
const TOLERANCIA = 3 * DIA;

export interface Licenca {
  codigo: string;
  plano: Plano;
  email: string;
  /** Número do pedido na plataforma (Kiwify/Hotmart) ou "manual-...". */
  pedido: string;
  /** Id da assinatura (Premium), para as renovações. */
  assinatura: string | null;
  origem: "kiwify" | "hotmart" | "manual";
  criadoEm: number;
  /** Premium: pago até. Os outros: null. */
  pagoAte: number | null;
  ativo: boolean;
  motivo: string | null;
  aparelhos: string[];
}

export interface ArmazemLicencas {
  salvar(l: Licenca): Promise<void>;
  porCodigo(codigo: string): Promise<Licenca | null>;
  porPedido(email: string, pedido: string): Promise<Licenca | null>;
  porAssinatura(assinatura: string): Promise<Licenca | null>;
  porPedidoOrigem(origem: string, pedido: string): Promise<Licenca | null>;
  listar(limite: number): Promise<Licenca[]>;
}

export class ArmazemLicencasMemoria implements ArmazemLicencas {
  itens = new Map<string, Licenca>();
  async salvar(l: Licenca) { this.itens.set(l.codigo, structuredClone(l)); }
  private achar(f: (l: Licenca) => boolean) { const l = [...this.itens.values()].find(f); return l ? structuredClone(l) : null; }
  async porCodigo(c: string) { return this.achar((l) => l.codigo === c); }
  async porPedido(email: string, pedido: string) { return this.achar((l) => l.email === email.toLowerCase() && l.pedido === pedido); }
  async porAssinatura(a: string) { return this.achar((l) => l.assinatura === a); }
  async porPedidoOrigem(o: string, p: string) { return this.achar((l) => l.origem === o && l.pedido === p); }
  async listar(limite: number) { return [...this.itens.values()].sort((a, b) => b.criadoEm - a.criadoEm).slice(0, limite); }
}

export class ArmazemLicencasPostgres implements ArmazemLicencas {
  private pronto: Promise<void> | null = null;
  constructor(private readonly pool: import("pg").Pool) {}
  private preparar() {
    this.pronto ??= this.pool
      .query(
        `CREATE TABLE IF NOT EXISTS licencas (codigo text PRIMARY KEY, email text NOT NULL, pedido text NOT NULL, origem text NOT NULL, assinatura text, criado bigint NOT NULL, dados jsonb NOT NULL);
         CREATE INDEX IF NOT EXISTS licencas_pedido ON licencas (email, pedido);
         CREATE INDEX IF NOT EXISTS licencas_assinatura ON licencas (assinatura);`,
      )
      .then(() => undefined, (e) => { this.pronto = null; throw e; });
    return this.pronto;
  }
  private async um(sql: string, p: unknown[]) {
    await this.preparar();
    return ((await this.pool.query(sql, p)).rows[0]?.dados as Licenca) ?? null;
  }
  async salvar(l: Licenca) {
    await this.preparar();
    await this.pool.query(
      `INSERT INTO licencas (codigo, email, pedido, origem, assinatura, criado, dados) VALUES ($1, $2, $3, $4, $5, $6, $7)
       ON CONFLICT (codigo) DO UPDATE SET email = $2, pedido = $3, origem = $4, assinatura = $5, dados = $7`,
      [l.codigo, l.email, l.pedido, l.origem, l.assinatura, l.criadoEm, JSON.stringify(l)],
    );
  }
  porCodigo(c: string) { return this.um("SELECT dados FROM licencas WHERE codigo = $1", [c]); }
  porPedido(e: string, p: string) { return this.um("SELECT dados FROM licencas WHERE email = $1 AND pedido = $2", [e.toLowerCase(), p]); }
  porAssinatura(a: string) { return this.um("SELECT dados FROM licencas WHERE assinatura = $1", [a]); }
  porPedidoOrigem(o: string, p: string) { return this.um("SELECT dados FROM licencas WHERE origem = $1 AND pedido = $2", [o, p]); }
  async listar(limite: number) {
    await this.preparar();
    return (await this.pool.query("SELECT dados FROM licencas ORDER BY criado DESC LIMIT $1", [limite])).rows.map((r) => r.dados as Licenca);
  }
}

/** Sem letras parecidas (0/O, 1/I/L). */
const ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
export function novoCodigo() {
  const bloco = () => Array.from({ length: 4 }, () => ALFABETO[randomInt(ALFABETO.length)]).join("");
  return `ABOT-${bloco()}-${bloco()}-${bloco()}`;
}

/** Até quando a licença dá direito a versões novas do app (ms). */
export function atualizacoesAte(l: Licenca): number {
  const p = PLANOS[l.plano];
  if (p.mensal) return l.pagoAte ?? l.criadoEm;
  const d = new Date(l.criadoEm);
  d.setMonth(d.getMonth() + (p.mesesAtualizacao ?? 0));
  return d.getTime();
}

/** Até quando o app funciona (null = para sempre). */
export function expiraEm(l: Licenca): number | null {
  return PLANOS[l.plano].mensal ? (l.pagoAte ?? l.criadoEm) + TOLERANCIA : null;
}

export interface Ativacao {
  token: string;
  codigo: string;
  plano: Plano;
  nomePlano: string;
  email: string;
  expira: number | null;
  atualizacoesAte: number;
}

/** Lê a chave privada do jeito que for colada no Render: PEM com quebras, com "\n" ou em base64. */
export function lerChavePrivada(bruto: string): KeyObject {
  let t = bruto.trim().replace(/\\n/g, "\n");
  if (!t.includes("BEGIN")) t = Buffer.from(t, "base64").toString("utf8");
  return createPrivateKey(t);
}

export function chavePublicaBase64(privada: KeyObject) {
  return createPublicKey(privada).export({ type: "spki", format: "der" }).toString("base64");
}

const b64url = (b: Buffer) => b.toString("base64url");

export class Licencas {
  constructor(
    private readonly armazem: ArmazemLicencas,
    private readonly chave: KeyObject,
    private readonly agora: () => number = Date.now,
  ) {}

  async criar(d: { plano: Plano; email: string; pedido?: string | null; origem: Licenca["origem"]; assinatura?: string | null; pagoAte?: number | null }): Promise<Licenca> {
    const pedido = d.pedido?.trim() || `manual-${Date.now().toString(36)}`;
    // Webhook repetido não cria outra licença.
    const existente = await this.armazem.porPedidoOrigem(d.origem, pedido);
    if (existente) return existente;
    const agora = this.agora();
    const l: Licenca = {
      codigo: novoCodigo(), plano: d.plano, email: d.email.trim().toLowerCase(), pedido, assinatura: d.assinatura ?? null, origem: d.origem,
      criadoEm: agora, pagoAte: PLANOS[d.plano].mensal ? (d.pagoAte ?? agora + 31 * DIA) : null, ativo: true, motivo: null, aparelhos: [],
    };
    await this.armazem.salvar(l);
    return l;
  }

  /** Ativa (ou reativa) num aparelho. Aceita o código ou o e-mail + número do pedido. */
  async ativar(p: { codigo?: string | null; email?: string | null; pedido?: string | null; aparelho: string }): Promise<Ativacao> {
    const codigo = p.codigo?.trim().toUpperCase();
    const l = codigo
      ? await this.armazem.porCodigo(codigo)
      : p.email && p.pedido ? await this.armazem.porPedido(p.email.trim().toLowerCase(), p.pedido.trim()) : null;
    if (!l) throw new IntegracaoErro("nao_encontrado", "Licença não encontrada. Confira o código (ou o e-mail e o número do pedido).", 404);
    if (!l.ativo) throw new IntegracaoErro("sem_acesso", `Licença desativada${l.motivo ? `: ${l.motivo}` : ""}.`, 403);
    const exp = expiraEm(l);
    if (exp !== null && this.agora() > exp) throw new IntegracaoErro("sem_acesso", "Assinatura vencida. Renove para continuar usando.", 402);
    if (!l.aparelhos.includes(p.aparelho)) {
      const max = PLANOS[l.plano].aparelhos;
      if (l.aparelhos.length >= max) {
        throw new IntegracaoErro("limite", `Esta licença já está em ${max} aparelho(s). Fale com o suporte para trocar de celular.`, 409);
      }
      l.aparelhos.push(p.aparelho);
      await this.armazem.salvar(l);
    }
    return this.assinar(l, p.aparelho);
  }

  private assinar(l: Licenca, aparelho: string): Ativacao {
    const corpo = { c: l.codigo, p: l.plano, e: expiraEm(l), u: atualizacoesAte(l), a: aparelho, v: this.agora() };
    const dados = Buffer.from(JSON.stringify(corpo));
    const assinatura = sign("sha256", dados, this.chave);
    return {
      token: `${b64url(dados)}.${b64url(assinatura)}`, codigo: l.codigo, plano: l.plano, nomePlano: PLANOS[l.plano].nome,
      email: l.email, expira: corpo.e, atualizacoesAte: corpo.u,
    };
  }

  async renovar(assinatura: string, pagoAte: number) {
    const l = await this.armazem.porAssinatura(assinatura);
    if (!l) return null;
    l.pagoAte = Math.max(l.pagoAte ?? 0, pagoAte);
    l.ativo = true;
    l.motivo = null;
    await this.armazem.salvar(l);
    return l;
  }

  async desativar(achar: { origem: Licenca["origem"]; pedido?: string | null; assinatura?: string | null }, motivo: string) {
    const l = (achar.assinatura ? await this.armazem.porAssinatura(achar.assinatura) : null)
      ?? (achar.pedido ? await this.armazem.porPedidoOrigem(achar.origem, achar.pedido) : null);
    if (!l) return null;
    l.ativo = false;
    l.motivo = motivo;
    await this.armazem.salvar(l);
    return l;
  }

  /** Assinatura cancelada: não renova, mas vale até o fim do que já foi pago. */
  async naoRenovar(assinatura: string) {
    const l = await this.armazem.porAssinatura(assinatura);
    if (!l) return null;
    l.motivo = "Assinatura cancelada (vale até o fim do período pago)";
    await this.armazem.salvar(l);
    return l;
  }

  async liberarAparelhos(codigo: string) {
    const l = await this.armazem.porCodigo(codigo.toUpperCase());
    if (!l) throw new IntegracaoErro("nao_encontrado", "Licença não encontrada", 404);
    l.aparelhos = [];
    await this.armazem.salvar(l);
    return l;
  }

  listar(limite = 200) { return this.armazem.listar(limite); }
  porCodigo(c: string) { return this.armazem.porCodigo(c.toUpperCase()); }
}

// ---------- Plataformas de venda ----------

/** "id:plano,id:plano". Sem o id no mapa, tenta pelo nome do produto. */
export function planoDoProduto(mapa: string | undefined, id: string | number | undefined | null, nome: string | undefined | null): Plano | null {
  const ids = Object.fromEntries((mapa ?? "").split(",").map((p) => p.split(":").map((s) => s.trim())).filter((p) => p.length === 2)) as Record<string, string>;
  const doMapa = id != null ? ids[String(id)] : undefined;
  if (doMapa && doMapa in PLANOS) return doMapa as Plano;
  const n = (nome ?? "").toLowerCase();
  for (const p of ["premium", "pro", "essencial"] as const) if (n.includes(p)) return p;
  return null;
}

export function hmacSha1Hex(corpo: string, segredo: string) {
  return createHmac("sha1", segredo).update(corpo).digest("hex");
}

export function iguais(a: string, b: string) {
  const x = Buffer.from(a);
  const y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}

type Evento =
  | { tipo: "compra"; plano: Plano; email: string; pedido: string; assinatura: string | null; pagoAte: number | null }
  | { tipo: "renovacao"; assinatura: string; pagoAte: number }
  | { tipo: "reembolso"; pedido: string | null; assinatura: string | null; motivo: string }
  | { tipo: "cancelamento"; assinatura: string }
  | { tipo: "ignorar"; motivo: string };

const data = (v: unknown): number | null => {
  if (typeof v === "number") return v < 1e12 ? v * 1000 : v;
  if (typeof v === "string" && v) { const t = Date.parse(v); return Number.isNaN(t) ? null : t; }
  return null;
};

/** Kiwify: webhook com order_status / webhook_event_type. */
export function eventoKiwify(b: Record<string, any>, mapa: string | undefined, agora: number): Evento {
  const status = String(b.order_status ?? "").toLowerCase();
  const tipo = String(b.webhook_event_type ?? "").toLowerCase();
  const pedido = String(b.order_id ?? b.order_ref ?? "");
  const assinatura = b.subscription_id ?? b.Subscription?.id ?? null;
  const proxima = data(b.Subscription?.next_payment) ?? agora + 31 * DIA;
  if (tipo.includes("refund") || tipo.includes("chargeback") || status === "refunded" || status === "chargedback") {
    return { tipo: "reembolso", pedido, assinatura, motivo: status === "chargedback" || tipo.includes("chargeback") ? "Chargeback" : "Reembolso" };
  }
  if (tipo.includes("subscription_canceled") || tipo.includes("subscription_cancelled")) return assinatura ? { tipo: "cancelamento", assinatura } : { tipo: "ignorar", motivo: "sem assinatura" };
  if (tipo.includes("subscription_renewed") && assinatura) return { tipo: "renovacao", assinatura, pagoAte: proxima + DIA };
  if (status !== "paid" && status !== "approved") return { tipo: "ignorar", motivo: `status ${status || tipo}` };
  const plano = planoDoProduto(mapa, b.Product?.product_id, b.Product?.product_name);
  if (!plano) return { tipo: "ignorar", motivo: "produto sem plano" };
  return { tipo: "compra", plano, email: String(b.Customer?.email ?? ""), pedido, assinatura, pagoAte: PLANOS[plano].mensal ? proxima + DIA : null };
}

/** Hotmart: webhook 2.0 (event + data). */
export function eventoHotmart(b: Record<string, any>, mapa: string | undefined, agora: number): Evento {
  const ev = String(b.event ?? "").toUpperCase();
  const d = b.data ?? {};
  const pedido = String(d.purchase?.transaction ?? "");
  const assinatura = d.subscription?.subscriber?.code ?? null;
  const proxima = data(d.purchase?.date_next_charge) ?? agora + 31 * DIA;
  if (ev === "PURCHASE_REFUNDED" || ev === "PURCHASE_CHARGEBACK" || ev === "PURCHASE_PROTEST") {
    return { tipo: "reembolso", pedido, assinatura, motivo: ev === "PURCHASE_REFUNDED" ? "Reembolso" : "Chargeback" };
  }
  if (ev === "SUBSCRIPTION_CANCELLATION") return assinatura ? { tipo: "cancelamento", assinatura } : { tipo: "ignorar", motivo: "sem assinatura" };
  if (ev !== "PURCHASE_APPROVED" && ev !== "PURCHASE_COMPLETE") return { tipo: "ignorar", motivo: ev };
  // Cobrança seguinte da mesma assinatura = renovação.
  if (assinatura && Number(d.purchase?.recurrence_number ?? 1) > 1) return { tipo: "renovacao", assinatura, pagoAte: proxima + DIA };
  const plano = planoDoProduto(mapa, d.product?.id, d.product?.name);
  if (!plano) return { tipo: "ignorar", motivo: "produto sem plano" };
  return { tipo: "compra", plano, email: String(d.buyer?.email ?? ""), pedido, assinatura, pagoAte: PLANOS[plano].mensal ? proxima + DIA : null };
}

export async function aplicarEvento(lic: Licencas, origem: "kiwify" | "hotmart", e: Evento) {
  switch (e.tipo) {
    case "compra":
      if (!e.email || !e.pedido) return { ok: false, motivo: "faltou e-mail ou pedido" };
      // Hotmart manda APPROVED e depois COMPLETE: o segundo não cria outra licença.
      return { ok: true, codigo: (await lic.criar({ plano: e.plano, email: e.email, pedido: e.pedido, origem, assinatura: e.assinatura, pagoAte: e.pagoAte })).codigo };
    case "renovacao":
      return { ok: !!(await lic.renovar(e.assinatura, e.pagoAte)) };
    case "reembolso":
      return { ok: !!(await lic.desativar({ origem, pedido: e.pedido, assinatura: e.assinatura }, e.motivo)) };
    case "cancelamento":
      return { ok: !!(await lic.naoRenovar(e.assinatura)) };
    default:
      return { ok: true, ignorado: e.motivo };
  }
}
