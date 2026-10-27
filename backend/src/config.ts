import "dotenv/config";
import { z } from "zod/v4";

const opcional = z
  .string()
  .optional()
  .transform((v) => (v && v.trim() !== "" ? v.trim() : undefined));

const Env = z.object({
  NODE_ENV: z.enum(["development", "production", "test"]).default("production"),
  PORT: z.coerce.number().int().positive().default(8787),
  HOST: z.string().default("0.0.0.0"),
  APP_TOKEN: z.string().min(32, "APP_TOKEN precisa de pelo menos 32 caracteres"),
  ANTHROPIC_API_KEY: opcional,
  AI_MODEL: z.string().default("claude-opus-5-5"),
  AI_DAILY_LIMIT: z.coerce.number().int().min(0).default(300),
  GEMINI_API_KEY: opcional,
  GEMINI_MODELS: z.string().default("gemini-flash-lite-latest,gemini-flash-latest"),
  SHOPEE_APP_ID: opcional,
  SHOPEE_SECRET: opcional,
  SHOPEE_ENDPOINT: z.url().default("https://open-api.affiliate.shopee.com.br/graphql"),
  ML_CLIENT_ID: opcional,
  ML_CLIENT_SECRET: opcional,
  ML_REFRESH_TOKEN: opcional,
  TELEGRAM_BOT_TOKEN: opcional,
  DATA_DIR: z.string().default("data"),
  /** Postgres (ex.: Supabase, Session pooler). Guarda a sessão do WhatsApp automático. */
  DATABASE_URL: opcional,
  WA_INTERVALO_MIN: z.coerce.number().int().min(1).default(10),
  WA_MAX_DIA: z.coerce.number().int().min(1).default(40),
  // ---------- Venda (só no servidor central do dono) ----------
  /** Chave privada ECDSA P-256 (PEM ou base64). Liga o modo central de licenças. */
  LICENCA_CHAVE_PRIVADA: opcional,
  /** Token do painel /admin. Mínimo 32 caracteres. */
  ADMIN_TOKEN: opcional,
  /** Token do webhook da Kiwify (assina o corpo com HMAC-SHA1). */
  KIWIFY_TOKEN: opcional,
  /** Hottok do webhook da Hotmart. */
  HOTMART_HOTTOK: opcional,
  /** "idDoProduto:plano,..." (planos: essencial, pro, premium). */
  PRODUTOS_PLANOS: opcional,
  /** Links de compra mostrados na página de vendas. */
  LINK_ESSENCIAL: opcional,
  LINK_PRO: opcional,
  LINK_PREMIUM: opcional,
  /** Link do suporte (ex.: https://wa.me/55...). */
  LINK_SUPORTE: opcional,
  /** Pasta com o APK e o versao.json. */
  RELEASES_DIR: z.string().default("releases"),
});

export type Config = z.infer<typeof Env>;

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const r = Env.safeParse(env);
  if (!r.success) {
    const msg = r.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; ");
    throw new Error(`Configuração inválida: ${msg}`);
  }
  return r.data;
}
