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
