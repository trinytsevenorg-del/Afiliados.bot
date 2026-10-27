import pg from "pg";
import { timingSafeEqual, createHash } from "node:crypto";
import Fastify, { type FastifyInstance } from "fastify";
import { z } from "zod/v4";
import type { Config } from "./config.js";
import { GeradorTexto, PedidoPrint, PedidoTexto } from "./integrations/ai.js";
import { MercadoLivre } from "./integrations/mercadolivre.js";
import { Shopee } from "./integrations/shopee.js";
import { Telegram } from "./integrations/telegram.js";
import { ArmazemComErro, ArmazemPostgres, normalizarUrlBanco, WhatsApp } from "./integrations/whatsapp.js";
import { IntegracaoErro } from "./integrations/types.js";
import { ArmazemFilaPostgres, FilaServidor } from "./fila.js";
import { ArmazemLicencasPostgres, lerChavePrivada, Licencas } from "./licencas.js";
import { registrarVenda, rotaPublica } from "./rotasVenda.js";
import { chavePublica, conferidorDeLicenca } from "./licencaApp.js";
import { analisarLink, extrairIds, LinkInvalido, validarUrl } from "./lib/links.js";

export interface Deps {
  shopee: Shopee;
  ml: MercadoLivre;
  ia: GeradorTexto;
  telegram: Telegram;
  whatsapp: WhatsApp;
  fila: FilaServidor;
  /** Só no servidor central do dono (LICENCA_CHAVE_PRIVADA + banco). */
  licencas: Licencas | null;
  fetchImpl?: typeof fetch;
}

export function criarDeps(cfg: Config): Deps {
  const banco = bancoDaConfig(cfg.DATABASE_URL);
  const telegram = new Telegram({ token: cfg.TELEGRAM_BOT_TOKEN, dataDir: cfg.DATA_DIR });
  const whatsapp = new WhatsApp(
    banco === null ? null : "erro" in banco ? new ArmazemComErro(banco.erro) : new ArmazemPostgres(banco.pool),
    { intervaloMin: cfg.WA_INTERVALO_MIN, maxDia: cfg.WA_MAX_DIA },
  );
  return {
    shopee: new Shopee({ appId: cfg.SHOPEE_APP_ID, secret: cfg.SHOPEE_SECRET, endpoint: cfg.SHOPEE_ENDPOINT }),
    ml: new MercadoLivre({ clientId: cfg.ML_CLIENT_ID, clientSecret: cfg.ML_CLIENT_SECRET, refreshToken: cfg.ML_REFRESH_TOKEN, dataDir: cfg.DATA_DIR }),
    ia: new GeradorTexto({
      apiKey: cfg.ANTHROPIC_API_KEY, modelo: cfg.AI_MODEL, limiteDiario: cfg.AI_DAILY_LIMIT,
      geminiKey: cfg.GEMINI_API_KEY, geminiModelos: cfg.GEMINI_MODELS.split(",").map((m) => m.trim()).filter(Boolean),
    }),
    telegram,
    whatsapp,
    fila: new FilaServidor(banco && "pool" in banco ? new ArmazemFilaPostgres(banco.pool) : null, { telegram, whatsapp }),
    licencas: banco && "pool" in banco && cfg.LICENCA_CHAVE_PRIVADA ? criarLicencas(banco.pool, cfg.LICENCA_CHAVE_PRIVADA) : null,
  };
}

function criarLicencas(pool: pg.Pool, chave: string) {
  try {
    return new Licencas(new ArmazemLicencasPostgres(pool), lerChavePrivada(chave));
  } catch (e) {
    // Chave colada errada não derruba o servidor: só desliga a venda.
    console.error("LICENCA_CHAVE_PRIVADA inválida:", (e as Error).message);
    return null;
  }
}

/** Um só pool de conexões para a sessão do WhatsApp e a fila. */
function bancoDaConfig(bruto: string | undefined): null | { erro: string } | { pool: pg.Pool } {
  if (!bruto) return null;
  const r = normalizarUrlBanco(bruto);
  if ("erro" in r) return r;
  const pool = new pg.Pool({ connectionString: r.url, ssl: { rejectUnauthorized: false }, max: 4 });
  // Conexão parada que cai (ex.: o Supabase reinicia) não pode derrubar o servidor.
  pool.on("error", () => undefined);
  return { pool };
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

const FotoEnvio = z.object({
  tipo: z.enum(["image/jpeg", "image/png", "image/webp"]),
  base64: z.string().min(100).max(10_000_000).regex(/^[A-Za-z0-9+/=]+$/),
});

const ComUrl = z.object({ url: z.string().min(8).max(2048) });

export async function criarApp(cfg: Config, deps: Deps): Promise<FastifyInstance> {
  const app = Fastify({
    bodyLimit: 32 * 1024,
    trustProxy: true,
    logger: cfg.NODE_ENV === "test" ? false : { level: "info", redact: { paths: ["req.headers.authorization", "req.headers.cookie"], censor: "[oculto]" } },
  });

  const geral = limitador(120);
  const licencaDoApp = conferidorDeLicenca(chavePublica());
  // Um lote de prints chama a IA várias vezes seguidas.
  const ia = limitador(40);

  app.addHook("onRequest", async (req, reply) => {
    if (!geral(req.ip)) return reply.code(429).send({ erro: "limite", mensagem: "Muitas requisições. Espere um minuto." });
    if (rotaPublica(req.url)) return;
    const h = req.headers.authorization ?? "";
    const tok = h.startsWith("Bearer ") ? h.slice(7) : "";
    if (!tok || !tokenIgual(tok, cfg.APP_TOKEN)) return reply.code(401).send({ erro: "nao_autorizado", mensagem: "Token do app ausente ou inválido" });
    if (cfg.LICENCA_LIVRE !== "1") {
      const l = licencaDoApp(String(req.headers["x-licenca"] ?? "") || undefined);
      if (!l.ok) return reply.code(402).send({ erro: "licenca", mensagem: l.motivo });
    }
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

  app.get("/api/health", async () => ({ ok: true, versao: "0.1.0", hora: new Date().toISOString(), banco: deps.whatsapp.banco }));

  app.get("/api/integrations/status", async (req) => {
    const testar = (req.query as { testar?: string }).testar === "1";
    const [shopee, telegram] = await Promise.all([deps.shopee.status(testar), deps.telegram.status(testar)]);
    return { integracoes: [shopee, deps.ml.status(), deps.ia.status(), telegram, deps.whatsapp.status()] };
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

  app.post("/api/publish/telegram", { bodyLimit: 40 * 1024 * 1024 }, async (req) => {
    const b = z
      .object({
        chave: z.string().min(8).max(100),
        chatId: z.string().regex(/^(@[A-Za-z0-9_]{5,32}|-?\d{5,20})$/, "Use @canal ou o ID numérico"),
        texto: z.string().min(1).max(4096),
        imagem: z.string().max(2048).nullish(),
        foto: FotoEnvio.nullish(),
        fotos: z.array(FotoEnvio).max(10).nullish(),
        formato: z.enum(["html"]).nullish(),
      })
      .parse(req.body);
    if (b.imagem && !b.imagem.startsWith("https://")) throw new LinkInvalido("A imagem precisa ser HTTPS");
    return deps.telegram.publicar(b);
  });

  app.get("/api/whatsapp/status", async () => deps.whatsapp.resumo());

  app.post("/api/whatsapp/connect", async (req) => {
    const { telefone } = z.object({ telefone: z.string().min(10).max(20) }).parse(req.body);
    return deps.whatsapp.conectar(telefone);
  });

  app.post("/api/whatsapp/disconnect", async () => {
    await deps.whatsapp.desconectar();
    return { ok: true };
  });

  app.get("/api/whatsapp/groups", async () => ({ grupos: await deps.whatsapp.grupos() }));

  app.post("/api/publish/whatsapp", { bodyLimit: 12 * 1024 * 1024 }, async (req) => {
    const b = z
      .object({
        chave: z.string().min(8).max(100),
        grupo: z.string().regex(/^[\d-]{5,40}@g\.us$/, "Grupo inválido"),
        texto: z.string().min(1).max(4096),
        foto: FotoEnvio.nullish(),
      })
      .parse(req.body);
    return deps.whatsapp.publicar(b);
  });

  // ---------- Fila no servidor (posta com o celular desligado) ----------
  app.get("/api/fila/status", async () => ({ ativa: deps.fila.ativa }));

  app.post("/api/fila", { bodyLimit: 40 * 1024 * 1024 }, async (req) => {
    const b = z
      .object({
        chave: z.string().min(8).max(100),
        canal: z.enum(["telegram", "whatsapp"]),
        destino: z.string().min(2).max(60),
        texto: z.string().min(1).max(4096),
        fotos: z.array(FotoEnvio).max(10).nullish(),
        imagem: z.string().max(2048).nullish(),
        quando: z.number().int().positive(),
        validoAte: z.number().int().positive().nullish(),
      })
      .parse(req.body);
    if (b.canal === "whatsapp" && !/^[\d-]{5,40}@g\.us$/.test(b.destino)) throw new IntegracaoErro("resposta_invalida", "Só é permitido postar em grupos", 400);
    if (b.canal === "telegram" && !/^(@[A-Za-z0-9_]{5,32}|-?\d{5,20})$/.test(b.destino)) throw new IntegracaoErro("resposta_invalida", "Use @canal ou o ID numérico", 400);
    if (b.imagem && !b.imagem.startsWith("https://")) throw new LinkInvalido("A imagem precisa ser HTTPS");
    return deps.fila.agendar(b);
  });

  app.post("/api/fila/consultar", async (req) => {
    const { chaves } = z.object({ chaves: z.array(z.string().min(8).max(100)).max(500) }).parse(req.body);
    return { itens: await deps.fila.consultar(chaves) };
  });

  app.post("/api/fila/cancelar", async (req) => {
    const { chave } = z.object({ chave: z.string().min(8).max(100) }).parse(req.body);
    const r = await deps.fila.cancelar(chave);
    if (!r) throw new IntegracaoErro("nao_encontrado", "Item não está na fila do servidor", 404);
    return r;
  });

  app.post("/api/telegram/test-destination", async (req) => {
    const { chatId } = z.object({ chatId: z.string().min(2).max(40) }).parse(req.body);
    return deps.telegram.testarDestino(chatId);
  });

  registrarVenda(app, cfg, deps.licencas, deps.fetchImpl);

  return app;
}
