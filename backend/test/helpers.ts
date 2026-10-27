import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import type { Config } from "../src/config.js";

export const TOKEN = "t".repeat(40);

export function cfgTeste(extra: Partial<Config> = {}): Config {
  return {
    NODE_ENV: "test",
    PORT: 0,
    HOST: "127.0.0.1",
    APP_TOKEN: TOKEN,
    AI_MODEL: "claude-opus-5-5",
    AI_DAILY_LIMIT: 5,
    SHOPEE_ENDPOINT: "https://open-api.affiliate.shopee.com.br/graphql",
    DATA_DIR: mkdtempSync(join(tmpdir(), "afb-")),
    ANTHROPIC_API_KEY: undefined,
    GEMINI_API_KEY: undefined,
    GEMINI_MODELS: "gemini-flash-lite-latest",
    SHOPEE_APP_ID: undefined,
    SHOPEE_SECRET: undefined,
    ML_CLIENT_ID: undefined,
    ML_CLIENT_SECRET: undefined,
    ML_REFRESH_TOKEN: undefined,
    TELEGRAM_BOT_TOKEN: undefined,
    DATABASE_URL: undefined,
    WA_INTERVALO_MIN: 10,
    WA_MAX_DIA: 40,
    LICENCA_CHAVE_PRIVADA: undefined,
    ADMIN_TOKEN: undefined,
    KIWIFY_TOKEN: undefined,
    HOTMART_HOTTOK: undefined,
    PRODUTOS_PLANOS: undefined,
    LINK_ESSENCIAL: undefined,
    LINK_PRO: undefined,
    LINK_PREMIUM: undefined,
    LINK_SUPORTE: undefined,
    RELEASES_DIR: "releases-teste-inexistente",
    ...extra,
  };
}

type Rota = (url: string, init: RequestInit) => Response | Promise<Response>;

/** fetch falso: registra chamadas e responde pela função dada. */
export function fakeFetch(rota: Rota) {
  const chamadas: { url: string; init: RequestInit }[] = [];
  const f = (async (input: string | URL | Request, init: RequestInit = {}) => {
    const url = typeof input === "string" ? input : input.toString();
    chamadas.push({ url, init });
    return rota(url, init);
  }) as typeof fetch;
  return { f, chamadas };
}

export const json = (body: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", ...headers } });
