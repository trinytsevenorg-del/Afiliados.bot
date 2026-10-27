import { createReadStream, existsSync, readFileSync, statSync } from "node:fs";
import { join, resolve } from "node:path";
import type { FastifyInstance } from "fastify";
import { z } from "zod/v4";
import type { Config } from "./config.js";
import { IntegracaoErro } from "./integrations/types.js";
import { aplicarEvento, atualizacoesAte, eventoHotmart, eventoKiwify, expiraEm, hmacSha1Hex, iguais, PLANOS, type Licencas } from "./licencas.js";
import { paginaAdmin, paginaSite } from "./paginas.js";

/** Rotas abertas (sem o token do app). O resto do backend continua exigindo o token. */
export const ROTAS_PUBLICAS = ["/api/health", "/api/licenca/", "/api/app/versao", "/api/webhook/", "/api/admin/", "/baixar", "/admin", "/site", "/"];

export function rotaPublica(url: string) {
  const caminho = url.split("?")[0] ?? "";
  return ROTAS_PUBLICAS.some((r) => (r.endsWith("/") && r !== "/" ? caminho.startsWith(r) : caminho === r));
}

export interface VersaoApp { versionCode: number; versao: string; data: number; novidades: string[] }

export function lerVersao(dir: string): VersaoApp | null {
  try {
    return JSON.parse(readFileSync(join(dir, "versao.json"), "utf8")) as VersaoApp;
  } catch {
    return null;
  }
}

/** Limite por IP para a ativação (evita tentar códigos no chute). */
function limitador(porMinuto: number) {
  const j = new Map<string, { t: number; n: number }>();
  return (ip: string) => {
    const agora = Date.now();
    const x = j.get(ip);
    if (!x || agora - x.t > 60_000) { j.set(ip, { t: agora, n: 1 }); if (j.size > 5000) j.clear(); return true; }
    return ++x.n <= porMinuto;
  };
}

export function registrarVenda(app: FastifyInstance, cfg: Config, licencas: Licencas | null) {
  const releases = resolve(cfg.RELEASES_DIR);
  const ativacoes = limitador(10);

  const exigirLicencas = () => {
    if (!licencas) throw new IntegracaoErro("nao_configurado", "Este servidor não vende licenças", 404);
    return licencas;
  };

  app.get("/", async (_req, reply) => (licencas ? reply.redirect("/site") : reply.code(404).send({ erro: "nao_encontrado", mensagem: "Use /api/health" })));
  app.get("/site", async (_req, reply) => {
    exigirLicencas();
    const v = lerVersao(releases);
    return reply.type("text/html; charset=utf-8").send(paginaSite({ essencial: cfg.LINK_ESSENCIAL, pro: cfg.LINK_PRO, premium: cfg.LINK_PREMIUM, suporte: cfg.LINK_SUPORTE, versao: v?.versao }));
  });
  app.get("/admin", async (_req, reply) => {
    exigirLicencas();
    return reply.type("text/html; charset=utf-8").send(paginaAdmin());
  });

  // ---------- App: ativação e atualização ----------
  app.post("/api/licenca/ativar", async (req) => {
    if (!ativacoes(req.ip)) throw new IntegracaoErro("limite", "Muitas tentativas. Espere um minuto.", 429);
    const b = z
      .object({
        codigo: z.string().max(40).nullish(),
        email: z.string().max(200).nullish(),
        pedido: z.string().max(100).nullish(),
        aparelho: z.string().min(8).max(100),
      })
      .parse(req.body);
    return exigirLicencas().ativar(b);
  });

  app.get("/api/app/versao", async () => {
    const v = lerVersao(releases);
    if (!v) throw new IntegracaoErro("nao_encontrado", "Nenhuma versão publicada", 404);
    return { ...v, url: "/baixar" };
  });

  app.get("/baixar", async (_req, reply) => {
    const apk = join(releases, "afiliados-bot.apk");
    if (!existsSync(apk)) throw new IntegracaoErro("nao_encontrado", "APK ainda não publicado", 404);
    const v = lerVersao(releases);
    return reply
      .header("content-type", "application/vnd.android.package-archive")
      .header("content-length", statSync(apk).size)
      .header("content-disposition", `attachment; filename="afiliados-bot-${v?.versao ?? "app"}.apk"`)
      .send(createReadStream(apk));
  });

  // ---------- Plataformas de venda ----------
  // Corpo cru: a Kiwify assina o texto exato que mandou.
  app.register(async (sub) => {
    sub.addContentTypeParser("application/json", { parseAs: "string", bodyLimit: 256 * 1024 }, (req, corpo, feito) => {
      (req as unknown as { cru: string }).cru = String(corpo);
      try { feito(null, JSON.parse(String(corpo) || "{}")); } catch (e) { feito(e as Error, undefined); }
    });

    sub.post("/api/webhook/kiwify", async (req) => {
      const lic = exigirLicencas();
      if (!cfg.KIWIFY_TOKEN) throw new IntegracaoErro("nao_configurado", "KIWIFY_TOKEN não configurado", 503);
      const assinatura = String((req.query as { signature?: string }).signature ?? "");
      const cru = (req as unknown as { cru?: string }).cru ?? "";
      if (!assinatura || !iguais(assinatura, hmacSha1Hex(cru, cfg.KIWIFY_TOKEN))) throw new IntegracaoErro("autenticacao", "Assinatura inválida", 401);
      return aplicarEvento(lic, "kiwify", eventoKiwify(req.body as Record<string, unknown>, cfg.PRODUTOS_PLANOS, Date.now()));
    });

    sub.post("/api/webhook/hotmart", async (req) => {
      const lic = exigirLicencas();
      if (!cfg.HOTMART_HOTTOK) throw new IntegracaoErro("nao_configurado", "HOTMART_HOTTOK não configurado", 503);
      const hottok = String(req.headers["x-hotmart-hottok"] ?? "");
      if (!hottok || !iguais(hottok, cfg.HOTMART_HOTTOK)) throw new IntegracaoErro("autenticacao", "Hottok inválido", 401);
      return aplicarEvento(lic, "hotmart", eventoHotmart(req.body as Record<string, unknown>, cfg.PRODUTOS_PLANOS, Date.now()));
    });
  });

  // ---------- Painel do dono ----------
  const admin = (req: { headers: Record<string, string | string[] | undefined> }) => {
    const lic = exigirLicencas();
    const h = String(req.headers.authorization ?? "");
    const tok = h.startsWith("Bearer ") ? h.slice(7) : "";
    if (!cfg.ADMIN_TOKEN || cfg.ADMIN_TOKEN.length < 32 || !tok || !iguais(tok, cfg.ADMIN_TOKEN)) {
      throw new IntegracaoErro("autenticacao", "Token do painel inválido", 401);
    }
    return lic;
  };
  const resumo = (l: Awaited<ReturnType<Licencas["listar"]>>[number]) => ({
    ...l, nomePlano: PLANOS[l.plano].nome, expira: expiraEm(l), atualizacoesAte: atualizacoesAte(l),
  });

  app.get("/api/admin/licencas", async (req) => ({ licencas: (await admin(req).listar(300)).map(resumo) }));

  app.post("/api/admin/licencas", async (req) => {
    const lic = admin(req);
    const b = z
      .object({ plano: z.enum(["essencial", "pro", "premium"]), email: z.email(), pedido: z.string().max(100).nullish(), meses: z.number().int().min(1).max(36).nullish() })
      .parse(req.body);
    const pagoAte = b.plano === "premium" ? Date.now() + (b.meses ?? 1) * 31 * 86_400_000 : null;
    return resumo(await lic.criar({ plano: b.plano, email: b.email, pedido: b.pedido, origem: "manual", pagoAte }));
  });

  app.post("/api/admin/licencas/:codigo/liberar", async (req) => resumo(await admin(req).liberarAparelhos((req.params as { codigo: string }).codigo)));

  app.post("/api/admin/licencas/:codigo/desativar", async (req) => {
    const lic = admin(req);
    const l = await lic.porCodigo((req.params as { codigo: string }).codigo);
    if (!l) throw new IntegracaoErro("nao_encontrado", "Licença não encontrada", 404);
    return resumo((await lic.desativar({ origem: l.origem, pedido: l.pedido }, "Desativada pelo dono"))!);
  });
}
