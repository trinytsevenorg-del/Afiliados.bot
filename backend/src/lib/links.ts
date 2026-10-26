// Validação e identificação de links. Só HTTPS e só domínios conhecidos.
import { fetchRetry } from "./http.js";

export type Marketplace = "shopee" | "mercadolivre";

const DOMINIOS: Record<Marketplace, string[]> = {
  shopee: ["shopee.com.br", "shope.ee"],
  mercadolivre: ["mercadolivre.com.br", "mercadolivre.com", "mercadolibre.com", "meli.la"],
};

export function marketplaceDoHost(host: string): Marketplace | null {
  const h = host.toLowerCase().replace(/\.$/, "");
  for (const [mp, doms] of Object.entries(DOMINIOS) as [Marketplace, string[]][]) {
    if (doms.some((d) => h === d || h.endsWith("." + d))) return mp;
  }
  return null;
}

export class LinkInvalido extends Error {}

export function validarUrl(bruto: string): URL {
  const texto = bruto.trim();
  if (texto.length > 2048) throw new LinkInvalido("Link longo demais");
  let u: URL;
  try {
    u = new URL(texto);
  } catch {
    throw new LinkInvalido("Isso não parece um link");
  }
  if (u.protocol !== "https:") throw new LinkInvalido("Só links HTTPS são aceitos");
  if (u.username || u.password) throw new LinkInvalido("Link com usuário/senha embutidos foi recusado");
  if (u.port && u.port !== "443") throw new LinkInvalido("Porta incomum recusada");
  if (!marketplaceDoHost(u.hostname)) throw new LinkInvalido("Domínio não reconhecido (só Shopee e Mercado Livre)");
  return u;
}

export interface IdsProduto {
  shopee?: { shopId: string; itemId: string };
  mercadolivre?: { itemId: string };
}

export function extrairIds(u: URL): IdsProduto {
  const mp = marketplaceDoHost(u.hostname);
  const caminho = decodeURIComponent(u.pathname);
  if (mp === "shopee") {
    const a = caminho.match(/-i\.(\d+)\.(\d+)/) ?? caminho.match(/\/product\/(\d+)\/(\d+)/);
    if (a) return { shopee: { shopId: a[1]!, itemId: a[2]! } };
  }
  if (mp === "mercadolivre") {
    const alvo = caminho + " " + (u.searchParams.get("item_id") ?? "") + " " + u.hash;
    const m = alvo.match(/\bMLB-?(\d{6,})/i);
    if (m) return { mercadolivre: { itemId: `MLB${m[1]}` } };
  }
  return {};
}

/** Sinais de que o link já é de afiliado. É só um indício, nunca confirmação. */
export function pareceAfiliado(u: URL): boolean {
  const h = u.hostname.toLowerCase();
  if (h === "s.shopee.com.br" || h === "shope.ee" || h.endsWith(".shope.ee")) return true;
  if (h === "meli.la" || h.endsWith(".meli.la")) return true;
  if (u.pathname.startsWith("/sec/")) return true;
  return u.searchParams.has("matt_tool") || u.searchParams.has("matt_word") || u.searchParams.get("utm_source")?.startsWith("an_") === true;
}

export interface Analise {
  marketplace: Marketplace;
  urlInformada: string;
  urlFinal: string;
  redirecionamentos: string[];
  ids: IdsProduto;
  pareceAfiliado: boolean;
  avisos: string[];
}

/**
 * Segue redirecionamentos manualmente (até 5), conferindo o domínio em cada passo.
 * Links encurtados (s.shopee.com.br, meli.la) viram o link do produto.
 */
export async function analisarLink(bruto: string, fetchImpl: typeof fetch = fetch): Promise<Analise> {
  const inicial = validarUrl(bruto);
  const avisos: string[] = [];
  const passos: string[] = [];
  let atual = inicial;
  for (let i = 0; i < 5; i++) {
    const ids = extrairIds(atual);
    if (ids.shopee || ids.mercadolivre) break;
    let res: Response;
    try {
      res = await fetchRetry(atual.toString(), { method: "GET", redirect: "manual", timeoutMs: 8000, retries: 1, fetchImpl, headers: { "user-agent": "AfiliadosBot/0.1 (+link-check)" } });
    } catch {
      avisos.push("Não foi possível abrir o link para seguir o redirecionamento");
      break;
    }
    await res.body?.cancel().catch(() => {});
    const loc = res.headers.get("location");
    if (res.status < 300 || res.status >= 400 || !loc) {
      if (res.status >= 400) avisos.push(`A loja respondeu ${res.status} para este link`);
      break;
    }
    const prox = new URL(loc, atual);
    try {
      validarUrl(prox.toString());
    } catch (e) {
      avisos.push(`Redirecionamento bloqueado para ${prox.hostname}: ${(e as Error).message}`);
      break;
    }
    passos.push(prox.toString());
    atual = prox;
  }
  const ids = extrairIds(atual);
  if (!ids.shopee && !ids.mercadolivre) avisos.push("Não deu para identificar o produto pelo link. Preencha à mão.");
  return {
    marketplace: marketplaceDoHost(atual.hostname)!,
    urlInformada: inicial.toString(),
    urlFinal: atual.toString(),
    redirecionamentos: passos,
    ids,
    pareceAfiliado: pareceAfiliado(inicial),
    avisos,
  };
}
