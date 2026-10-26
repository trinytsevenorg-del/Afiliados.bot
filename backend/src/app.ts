import { timingSafeEqual, createHash } from "node:crypto";
import Fastify, { type FastifyInstance } from "fastify";
import { z } from "zod/v4";
import type { Config } from "./config.js";
import { GeradorTexto, PedidoPrint, PedidoTexto } from "./integrations/ai.js";
import { MercadoLivre } from "./integrations/mercadolivre.js";
import { Shopee } from "./integrations/shopee.js";
import { Telegram, statusWhatsapp } from "./integrations/telegram.js";
import { IntegracaoErro } from "./integrations/types.js";
import { analisarLink, extrairIds, LinkInvalido, validarUrl } from "./lib/links.js";

export interface Deps {
  shopee: Shopee;
  ml: MercadoLivre;
  ia: GeradorTexto;
  telegram: Telegram;
  fetchImpl?: typeof fetch;
}

export function criarDeps(cfg: Config): Deps {
  return {
    shopee: new Shopee({ appId: cfg.SHOPEE_APP_ID, secret: cfg.SHOPEE_SECRET, endpoint: cfg.SHOPEE_ENDPOINT }),
    ml: new MercadoLivre({ clientId: cfg.ML_CLIENT_ID, clientSecret: cfg.ML_CLIENT_SECRET, refreshToken: cfg.ML_REFRESH_TOKEN, dataDir: cfg.DATA_DIR }),
    ia: new GeradorTexto({ apiKey: cfg.ANTHROPIC_API_KEY, modelo: cfg.AI_MODEL, limiteDiario: cfg.AI_DAILY_LIMIT }),
    telegram: new Telegram({ token: cfg.TELEGRAM_BOT_TOKEN, dataDir: cfg.DATA_DIR }),
  };
}

function tokenIgual(recebido: string, esperado: string) {
  const a = createHash("sha256").update(recebido).digest();
  const b = createHash("sha256").update(esperado).digest();
  return timingSafeEqual(a, b);
}

/** Limite simples por IP: N pedidos por minuto. Suficiente para uso pessoal. */
function limitador(porMinuto: number) {
  const janelas = new Map<string, { inicio: number; n: number }>();
  return (chave: string) => {
    const agora = Date.now();
    const j = janelas.get(chave);
    if (!j || agora - j.inicio > 60_000) {
      janelas.set(chave, { inicio: agora, n: 1 });
      if (janelas.size > 5000) janelas.clear();
      return true;
    }
    j.n++;
    return j.n <= porMinuto;
  };
}

const ComUrl = z.object({ url: z.string().min(8).max(2048) });

export async function criarApp(cfg: Config, deps: Deps): Promise<FastifyInstance> {
  const app = Fastify({
    bodyLimit: 32 * 1024,
    trustProxy: true,
    logger: cfg.NODE_ENV === "test" ? false : { level: "info", redact: { paths: ["req.headers.authorization", "req.headers.cookie"], censor: "[oculto]" } },
  });

  const geral = limitador(120);
  const ia = limitador(10);

  app.addHook("onRequest", async (req, reply) => {
    if (!geral(req.ip)) return reply.code(429).send({ erro: "limite", mensagem: "Muitas requisições. Espere um minuto." });
    if (req.url === "/api/health") return;
    const h = req.headers.authorization ?? "";
    const tok = h.startsWith("Bearer ") ? h.slice(7) : "";
    if (!tok || !tokenIgual(tok, cfg.APP_TOKEN)) return reply.code(401).send({ erro: "nao_autorizado", mensagem: "Token do app ausente ou inválido" });
  });

  app.setErrorHandler((err, _req, reply) => {
    if (err instanceof IntegracaoErro) return reply.code(err.httpStatus).send({ erro: err.codigo, mensagem: err.message });
    if (err instanceof LinkInvalido) return reply.code(400).send({ erro: "link_invalido", mensagem: err.message });
    if (err instanceof z.ZodError) return reply.code(400).send({ erro: "dados_invalidos", mensagem: "Dados inválidos", detalhes: err.issues.map((i) => `${i.path.join(".")}: ${i.message}`) });
    const e = err as { statusCode?: number; message?: string };
    if (e.statusCode && e.statusCode < 500) return reply.code(e.statusCode).send({ erro: "requisicao", mensagem: e.message });
    app.log.error({ err }, "erro interno");
    return reply.code(500).send({ erro: "interno", mensagem: "Erro interno no backend" });
  });

  app.get("/api/health", async () => ({ ok: true, versao: "0.1.0", hora: new Date().toISOString() }));

  app.get("/api/integrations/status", async (req) => {
    const testar = (req.query as { testar?: string }).testar === "1";
    const [shopee, telegram] = await Promise.all([deps.shopee.status(testar), deps.telegram.status(testar)]);
    return { integracoes: [shopee, deps.ml.status(), deps.ia.status(), telegram, statusWhatsapp()] };
  });

  app.post("/api/links/analyze", async (req) => {
    const { url } = ComUrl.parse(req.body);
    return analisarLink(url, deps.fetchImpl);
  });

  app.post("/api/shopee/product", async (req) => {
    const b = z.object({ url: z.string().max(2048).optional(), shopId: z.string().regex(/^\d+$/).optional(), itemId: z.string().regex(/^\d+$/).optional() }).parse(req.body);
    let ids = b.shopId && b.itemId ? { shopId: b.shopId, itemId: b.itemId } : undefined;
    if (!ids && b.url) ids = extrairIds(validarUrl(b.url)).shopee;
    if (!ids) throw new IntegracaoErro("nao_encontrado", "Não foi possível identificar o produto da Shopee", 400);
    return deps.shopee.produto(ids.shopId, ids.itemId);
  });

  app.post("/api/shopee/affiliate-link", async (req) => {
    const b = z.object({ url: z.string().max(2048), subIds: z.array(z.string().regex(/^[A-Za-z0-9]{1,50}$/)).max(5).default([]) }).parse(req.body);
    const u = validarUrl(b.url);
    if (!u.hostname.endsWith("shopee.com.br")) throw new LinkInvalido("Só links da Shopee podem ser convertidos aqui");
    return deps.shopee.linkCurto(u.toString(), b.subIds);
  });

  app.post("/api/ml/product", async (req) => {
    const b = z.object({ url: z.string().max(2048).optional(), itemId: z.string().optional() }).parse(req.body);
    const id = b.itemId?.toUpperCase().replace("-", "") ?? (b.url ? extrairIds(validarUrl(b.url)).mercadolivre?.itemId : undefined);
    if (!id) throw new IntegracaoErro("nao_encontrado", "Não foi possível identificar o produto do Mercado Livre", 400);
    return deps.ml.produto(id);
  });

  app.post("/api/copy/generate", async (req, reply) => {
    if (!ia(req.ip)) return reply.code(429).send({ erro: "limite", mensagem: "Muitas gerações seguidas. Espere um minuto." });
    return deps.ia.gerar(PedidoTexto.parse(req.body));
  });

  // Prints são grandes: limite próprio só nesta rota.
  app.post("/api/product/from-images", { bodyLimit: 40 * 1024 * 1024 }, async (req, reply) => {
    if (!ia(req.ip)) return reply.code(429).send({ erro: "limite", mensagem: "Muitos usos seguidos da IA. Espere um minuto." });
    return deps.ia.lerPrint(PedidoPrint.parse(req.body));
  });

  app.post("/api/publish/telegram", async (req) => {
    const b = z
      .object({
        chave: z.string().min(8).max(100),
        chatId: z.string().regex(/^(@[A-Za-z0-9_]{5,32}|-?\d{5,20})$/, "Use @canal ou o ID numérico"),
        texto: z.string().min(1).max(4096),
        imagem: z.string().max(2048).nullish(),
      })
      .parse(req.body);
    if (b.imagem && !b.imagem.startsWith("https://")) throw new LinkInvalido("A imagem precisa ser HTTPS");
    return deps.telegram.publicar(b);
  });

  app.post("/api/telegram/test-destination", async (req) => {
    const { chatId } = z.object({ chatId: z.string().min(2).max(40) }).parse(req.body);
    return deps.telegram.testarDestino(chatId);
  });

  return app;
}
