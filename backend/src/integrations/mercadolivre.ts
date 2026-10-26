// Mercado Livre: só CONSULTA de produto (API oficial, exige token OAuth).
// Não existe API oficial pública para gerar link de afiliado: isso fica manual.
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { fetchRetry } from "../lib/http.js";
import { IntegracaoErro, type StatusIntegracao } from "./types.js";

const API = "https://api.mercadolibre.com";

export interface MlCreds {
  clientId?: string;
  clientSecret?: string;
  refreshToken?: string;
  dataDir: string;
}

interface Token {
  access: string;
  refresh: string;
  expiraEm: number;
}

export class MercadoLivre {
  private token: Token | null = null;

  constructor(
    private readonly creds: MlCreds,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  get configurada() {
    return Boolean(this.creds.clientId && this.creds.clientSecret && this.creds.refreshToken);
  }

  private get arquivo() {
    return join(this.creds.dataDir, "ml-token.json");
  }

  /** O refresh token do ML muda a cada uso; guardamos o novo em disco. */
  private async refreshAtual(): Promise<string | undefined> {
    try {
      const salvo = JSON.parse(await readFile(this.arquivo, "utf8")) as { refresh?: string };
      return salvo.refresh ?? this.creds.refreshToken;
    } catch {
      return this.creds.refreshToken;
    }
  }

  private async renovar(): Promise<Token> {
    const refresh = await this.refreshAtual();
    if (!this.creds.clientId || !this.creds.clientSecret || !refresh) throw new IntegracaoErro("nao_configurado", "Mercado Livre não configurado (ML_CLIENT_ID, ML_CLIENT_SECRET, ML_REFRESH_TOKEN)", 503);
    const res = await fetchRetry(`${API}/oauth/token`, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded", accept: "application/json" },
      body: new URLSearchParams({ grant_type: "refresh_token", client_id: this.creds.clientId, client_secret: this.creds.clientSecret, refresh_token: refresh }),
      timeoutMs: 10_000,
      retries: 1,
      fetchImpl: this.fetchImpl,
    });
    if (res.status === 400 || res.status === 401) throw new IntegracaoErro("autenticacao", "O Mercado Livre recusou o refresh token. Autorize o app de novo.", 401);
    if (!res.ok) throw new IntegracaoErro("indisponivel", `Mercado Livre respondeu ${res.status} ao renovar o token`);
    const j = (await res.json()) as { access_token: string; refresh_token: string; expires_in: number };
    const t = { access: j.access_token, refresh: j.refresh_token, expiraEm: Date.now() + (j.expires_in - 120) * 1000 };
    await mkdir(this.creds.dataDir, { recursive: true });
    await writeFile(this.arquivo, JSON.stringify({ refresh: t.refresh }), { mode: 0o600 });
    this.token = t;
    return t;
  }

  private async get(path: string, tentarDeNovo = true): Promise<Response> {
    const t = this.token && this.token.expiraEm > Date.now() ? this.token : await this.renovar();
    const res = await fetchRetry(`${API}${path}`, { headers: { authorization: `Bearer ${t.access}` }, timeoutMs: 10_000, retries: 2, fetchImpl: this.fetchImpl });
    if (res.status === 401 && tentarDeNovo) {
      this.token = null;
      return this.get(path, false);
    }
    return res;
  }

  async produto(itemId: string) {
    if (!/^MLB\d{6,}$/.test(itemId)) throw new IntegracaoErro("nao_encontrado", "Código de produto do Mercado Livre inválido", 400);
    const res = await this.get(`/items/${itemId}`);
    if (res.status === 404) throw new IntegracaoErro("nao_encontrado", "Produto não encontrado no Mercado Livre", 404);
    if (res.status === 403) throw new IntegracaoErro("sem_acesso", "O Mercado Livre bloqueou a consulta (permissão do app ou validação de dados)", 403);
    if (res.status === 429) throw new IntegracaoErro("limite", "Limite de requisições do Mercado Livre. Tente mais tarde.", 429);
    if (!res.ok) throw new IntegracaoErro("indisponivel", `Mercado Livre respondeu ${res.status}`);
    const j = (await res.json()) as {
      id: string; title?: string; price?: number; original_price?: number | null; currency_id?: string;
      status?: string; permalink?: string; thumbnail?: string; pictures?: { secure_url?: string }[];
    };
    return {
      fonte: "mercadolivre_items_api" as const,
      consultadoEm: new Date().toISOString(),
      itemId: j.id,
      nome: j.title ?? null,
      preco: typeof j.price === "number" ? j.price : null,
      // Preço anterior informado pelo próprio Mercado Livre (não calculado por nós).
      precoAnterior: typeof j.original_price === "number" ? j.original_price : null,
      moeda: j.currency_id ?? null,
      situacao: j.status ?? null,
      linkProduto: j.permalink ?? null,
      imagem: j.pictures?.[0]?.secure_url ?? j.thumbnail ?? null,
      // Comissão e link de afiliado não vêm desta API.
      comissaoPercentual: null,
      linkAfiliado: null,
    };
  }

  status(): StatusIntegracao {
    const ok = this.configurada;
    return {
      id: "mercadolivre",
      nome: "Mercado Livre",
      estado: ok ? "configurado" : "nao_configurado",
      detalhe: ok ? "Consulta de produto liberada. O link de afiliado é sempre colado à mão." : "Faltam as credenciais do app do Mercado Livre",
      verificadoEm: new Date().toISOString(),
      comoConfigurar:
        "1) Crie um app em developers.mercadolivre.com.br. 2) Autorize com sua conta e pegue o refresh token (fluxo authorization_code). 3) Preencha ML_CLIENT_ID, ML_CLIENT_SECRET e ML_REFRESH_TOKEN. O link de afiliado é gerado no painel de Afiliados do Mercado Livre e colado no app.",
      operacoes: [
        { id: "produto", descricao: "Buscar nome, preço, preço anterior e imagem", implementada: true, disponivel: ok, motivo: ok ? undefined : "Não configurado" },
        { id: "link_afiliado", descricao: "Gerar link de afiliado", implementada: false, disponivel: false, motivo: "O Mercado Livre não oferece API pública para isso. Gere no painel de Afiliados e cole no app." },
        { id: "comissao", descricao: "Consultar comissão", implementada: false, disponivel: false, motivo: "Sem fonte oficial por API. Informe à mão." },
      ],
    };
  }
}
