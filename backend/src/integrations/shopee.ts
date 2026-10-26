// Shopee Afiliados (Open API GraphQL). Exige conta de afiliado aprovada para a API.
// Assinatura: SHA256(AppId + Timestamp + Payload + Secret), no cabeçalho Authorization.
import { createHash } from "node:crypto";
import { fetchRetry } from "../lib/http.js";
import { IntegracaoErro, type StatusIntegracao } from "./types.js";

export interface ShopeeCreds {
  appId?: string;
  secret?: string;
  endpoint: string;
}

export function assinar(appId: string, secret: string, timestamp: number, payload: string): string {
  const sig = createHash("sha256").update(`${appId}${timestamp}${payload}${secret}`).digest("hex");
  return `SHA256 Credential=${appId}, Timestamp=${timestamp}, Signature=${sig}`;
}

interface GqlErro {
  message: string;
  extensions?: { code?: number; message?: string };
}

function traduzirErro(e: GqlErro): IntegracaoErro {
  const c = e.extensions?.code;
  if (c === 10035) return new IntegracaoErro("sem_acesso", "Sua conta ainda não tem acesso à Open API de Afiliados da Shopee", 403);
  if (c === 10020 || c === 10031 || c === 10032) return new IntegracaoErro("autenticacao", "Credenciais da Shopee recusadas (App ID, Secret ou relógio)", 401);
  if (c === 10030) return new IntegracaoErro("limite", "Limite de requisições da Shopee atingido. Tente mais tarde.", 429);
  return new IntegracaoErro("indisponivel", `Shopee: ${e.message}`.slice(0, 300));
}

export class Shopee {
  constructor(
    private readonly creds: ShopeeCreds,
    private readonly fetchImpl: typeof fetch = fetch,
    private readonly agora: () => number = () => Math.floor(Date.now() / 1000),
  ) {}

  get configurada() {
    return Boolean(this.creds.appId && this.creds.secret);
  }

  async gql<T>(query: string, variables: Record<string, unknown> = {}): Promise<T> {
    if (!this.creds.appId || !this.creds.secret) throw new IntegracaoErro("nao_configurado", "Shopee não configurada (SHOPEE_APP_ID e SHOPEE_SECRET)", 503);
    const payload = JSON.stringify({ query, variables });
    const res = await fetchRetry(this.creds.endpoint, {
      method: "POST",
      headers: { "content-type": "application/json", authorization: assinar(this.creds.appId, this.creds.secret, this.agora(), payload) },
      body: payload,
      timeoutMs: 10_000,
      retries: 2,
      fetchImpl: this.fetchImpl,
    });
    let json: { data?: T; errors?: GqlErro[] };
    try {
      json = (await res.json()) as typeof json;
    } catch {
      throw new IntegracaoErro("resposta_invalida", `Shopee respondeu ${res.status} sem JSON`);
    }
    if (json.errors?.length) throw traduzirErro(json.errors[0]!);
    if (!res.ok || !json.data) throw new IntegracaoErro("indisponivel", `Shopee respondeu ${res.status}`);
    return json.data;
  }

  async produto(shopId: string, itemId: string) {
    const q = `query($shopId: Int64, $itemId: Int64) {
      productOfferV2(shopId: $shopId, itemId: $itemId, limit: 1) {
        nodes { itemId shopId productName shopName price priceMin priceMax priceDiscountRate
                commissionRate commission imageUrl productLink offerLink ratingStar sales periodEndTime }
      }
    }`;
    const d = await this.gql<{ productOfferV2: { nodes: Record<string, unknown>[] } }>(q, { shopId: Number(shopId), itemId: Number(itemId) });
    const n = d.productOfferV2?.nodes?.[0];
    if (!n) throw new IntegracaoErro("nao_encontrado", "A Shopee não retornou esse produto na lista de ofertas de afiliado", 404);
    const num = (v: unknown) => (v === null || v === undefined || v === "" ? null : Number(v));
    const taxa = num(n.commissionRate);
    return {
      fonte: "shopee_affiliate_api" as const,
      consultadoEm: new Date().toISOString(),
      itemId: String(n.itemId),
      shopId: String(n.shopId),
      nome: (n.productName as string) ?? null,
      loja: (n.shopName as string) ?? null,
      preco: num(n.priceMin ?? n.price),
      precoMax: num(n.priceMax),
      // A taxa vem como fração (0.1 = 10%). Convertemos para porcentagem.
      comissaoPercentual: taxa === null ? null : Math.round(taxa * 10000) / 100,
      comissaoValor: num(n.commission),
      descontoPercentualInformado: num(n.priceDiscountRate),
      imagem: (n.imageUrl as string) ?? null,
      linkProduto: (n.productLink as string) ?? null,
      linkAfiliado: (n.offerLink as string) ?? null,
      avaliacao: num(n.ratingStar),
      vendas: num(n.sales),
      ofertaValidaAte: n.periodEndTime ? new Date(Number(n.periodEndTime) * 1000).toISOString() : null,
    };
  }

  async linkCurto(origemUrl: string, subIds: string[] = []) {
    const q = `mutation($url: String!, $subIds: [String]) { generateShortLink(input: { originUrl: $url, subIds: $subIds }) { shortLink } }`;
    const d = await this.gql<{ generateShortLink: { shortLink: string } }>(q, { url: origemUrl, subIds: subIds.slice(0, 5) });
    const link = d.generateShortLink?.shortLink;
    if (!link || !link.startsWith("https://")) throw new IntegracaoErro("resposta_invalida", "A Shopee não devolveu um link válido");
    return { linkAfiliado: link, fonte: "shopee_affiliate_api" as const, confirmado: true, geradoEm: new Date().toISOString() };
  }

  async status(testar: boolean): Promise<StatusIntegracao> {
    const base = {
      id: "shopee" as const,
      nome: "Shopee Afiliados",
      verificadoEm: new Date().toISOString(),
      comoConfigurar:
        "1) Seja afiliado em affiliate.shopee.com.br. 2) Peça acesso à Open API no painel (Ferramentas > Open API). 3) Copie App ID e Secret para SHOPEE_APP_ID e SHOPEE_SECRET no .env do backend.",
    };
    const ops = (ok: boolean, motivo?: string) => [
      { id: "produto", descricao: "Buscar dados e comissão do produto", implementada: true, disponivel: ok, motivo },
      { id: "link_afiliado", descricao: "Gerar link de afiliado oficial", implementada: true, disponivel: ok, motivo },
      { id: "relatorio_conversoes", descricao: "Relatório de conversões", implementada: false, disponivel: false, motivo: "Ainda não implementado" },
    ];
    if (!this.configurada) return { ...base, estado: "nao_configurado", detalhe: "Faltam SHOPEE_APP_ID e SHOPEE_SECRET", operacoes: ops(false, "Não configurado") };
    if (!testar) return { ...base, estado: "configurado", detalhe: "Credenciais presentes, ainda não testadas", operacoes: ops(true) };
    try {
      await this.gql(`{ productOfferV2(limit: 1) { nodes { itemId } } }`);
      return { ...base, estado: "configurado", detalhe: "Conexão testada com sucesso", operacoes: ops(true) };
    } catch (e) {
      const err = e as IntegracaoErro;
      const estado = err.codigo === "sem_acesso" ? "aguardando_autorizacao" : err.codigo === "autenticacao" ? "erro_autenticacao" : "indisponivel";
      return { ...base, estado, detalhe: err.message, operacoes: ops(false, err.message) };
    }
  }
}
