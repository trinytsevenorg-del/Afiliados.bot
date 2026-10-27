import { createReadStream, existsSync, readFileSync, statSync } from "node:fs";
import { join, resolve } from "node:path";
import type { FastifyInstance } from "fastify";
import { z } from "zod/v4";
import type { Config } from "./config.js";
import { IntegracaoErro } from "./integrations/types.js";
import { aplicarEvento, atualizacoesAte, eventoHotmart, eventoKiwify, expiraEm, hmacSha1Hex, iguais, PLANOS, type Licencas } from "./licencas.js";
import { paginaAdmin, paginaSite } from "./paginas.js";
import { EmailBrevo, textoLicenca } from "./email.js";

/** Rotas abertas (sem o token do app). O resto do backend continua exigindo o token. */
export const ROTAS_PUBLICAS = ["/api/health", "/api/licenca/", "/api/app/versao", "/api/webhook/", "/api/admin/", "/baixar", "/admin", "/site", "/"];

export function rotaPublica(url: string) {
  const caminho = url.split("?")[0] ?? "";
  return ROTAS_PUBLICAS.some((r) => (r.endsWith("/") && r !== "/" ? caminho.startsWith(r) : caminho === r));
}

export interface VersaoApp { versionCode: number; versao: string; data: number; arquivo: string; novidades: string[] }

/** releases/versoes.json: todas as versões publicadas (o Essencial pode baixar a do plano dele). */
export function lerVersoes(dir: string): VersaoApp[] {
  try {
    const j = JSON.parse(readFileSync(join(dir, "versoes.json"), "utf8")) as { versoes: VersaoApp[] };
    return [...j.versoes].sort((a, b) => b.versionCode - a.versionCode);
  } catch {
    return [];
  }
}

/** A mais nova; com [ate], a mais nova lançada até essa data. */
export function lerVersao(dir: string, ate?: number): VersaoApp | null {
  return lerVersoes(dir).find((v) => ate === undefined || v.data <= ate) ?? null;
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

export function registrarVenda(app: FastifyInstance, cfg: Config, licencas: Licencas | null, fetchImpl: typeof fetch = fetch) {
  const releases = resolve(cfg.RELEASES_DIR);
  const site = (cfg.SITE_URL ?? process.env.RENDER_EXTERNAL_URL ?? "https://achadinhos-flqb.onrender.com").replace(/\/+$/, "");
  const suporte = cfg.LINK_SUPORTE ?? (cfg.WHATSAPP_VENDAS ? `https://wa.me/${cfg.WHATSAPP_VENDAS}` : undefined);
  const email = new EmailBrevo({ chave: cfg.BREVO_API_KEY, remetente: cfg.EMAIL_REMETENTE, nome: cfg.EMAIL_NOME, site, suporte }, fetchImpl);
  /** Botão de compra: link da plataforma, ou o WhatsApp de vendas com a mensagem pronta. */
  const comprar = (link: string | undefined, plano: string) =>
    link ?? (cfg.WHATSAPP_VENDAS ? `https://wa.me/${cfg.WHATSAPP_VENDAS}?text=${encodeURIComponent(`Oi! Quero o plano ${plano} do Achadinhos App. Meu e-mail é: `)}` : undefined);
  const ativacoes = limitador(10);

  const exigirLicencas = () => {
    if (!licencas) throw new IntegracaoErro("nao_configurado", "Este servidor não vende licenças", 404);
    return licencas;
  };

  app.get("/", async (_req, reply) => (licencas ? reply.redirect("/site") : reply.code(404).send({ erro: "nao_encontrado", mensagem: "Use /api/health" })));
  app.get("/site", async (_req, reply) => {
    exigirLicencas();
    const v = lerVersao(releases);
    return reply.type("text/html; charset=utf-8").send(paginaSite({
      essencial: comprar(cfg.LINK_ESSENCIAL, "Essencial"), pro: comprar(cfg.LINK_PRO, "Pro"), premium: comprar(cfg.LINK_PREMIUM, "Premium"),
      suporte, versao: v?.versao, porWhatsapp: !cfg.LINK_ESSENCIAL && !!cfg.WHATSAPP_VENDAS,
    }));
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
    const { arquivo: _a, ...resto } = v;
    return { ...resto, url: "/baixar" };
  });

  // ?ate=<ms>: a versão mais nova até essa data (fim das atualizações do plano).
  app.get("/baixar", async (req, reply) => {
    const ate = Number((req.query as { ate?: string }).ate);
    const v = lerVersao(releases, Number.isFinite(ate) && ate > 0 ? ate : undefined);
    const apk = v ? join(releases, v.arquivo.replace(/[^\w.-]/g, "")) : "";
    if (!v || !existsSync(apk)) throw new IntegracaoErro("nao_encontrado", "APK ainda não publicado", 404);
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
    const l = await lic.criar({ plano: b.plano, email: b.email, pedido: b.pedido, origem: "manual", pagoAte });
    return { ...resumo(l), ...(await mandar(l)) };
  });

  /** Manda a chave por e-mail. Se não der, o painel mostra a mensagem pronta para o WhatsApp. */
  const mandar = async (l: NonNullable<Awaited<ReturnType<Licencas["porCodigo"]>>>) => {
    const whatsapp = textoLicenca(l, site, suporte).texto;
    if (!email.configurado) return { emailEnviado: false, erroEmail: "E-mail não configurado (BREVO_API_KEY e EMAIL_REMETENTE)", whatsapp };
    try {
      await email.enviarLicenca(l);
      return { emailEnviado: true, whatsapp };
    } catch (e) {
      return { emailEnviado: false, erroEmail: (e as Error).message, whatsapp };
    }
  };

  app.post("/api/admin/licencas/:codigo/reenviar", async (req) => {
    const lic = admin(req);
    const l = await lic.porCodigo((req.params as { codigo: string }).codigo);
    if (!l) throw new IntegracaoErro("nao_encontrado", "Licença não encontrada", 404);
    return { ...resumo(l), ...(await mandar(l)) };
  });

  app.post("/api/admin/licencas/:codigo/liberar", async (req) => resumo(await admin(req).liberarAparelhos((req.params as { codigo: string }).codigo)));

  app.post("/api/admin/licencas/:codigo/desativar", async (req) => {
    const lic = admin(req);
    const l = await lic.porCodigo((req.params as { codigo: string }).codigo);
    if (!l) throw new IntegracaoErro("nao_encontrado", "Licença não encontrada", 404);
    return resumo((await lic.desativar({ origem: l.origem, pedido: l.pedido }, "Desativada pelo dono"))!);
  });
}
