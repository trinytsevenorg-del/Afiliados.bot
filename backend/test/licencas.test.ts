import { generateKeyPairSync, verify } from "node:crypto";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { criarApp, criarDeps } from "../src/app.js";
import { ArmazemLicencasMemoria, atualizacoesAte, eventoHotmart, eventoKiwify, hmacSha1Hex, Licencas, planoDoProduto } from "../src/licencas.js";
import { cfgTeste } from "./helpers.js";

const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "P-256" });
const DIA = 86_400_000;
const ADMIN = "a".repeat(40);

function lic(agora = () => 1_800_000_000_000) {
  return new Licencas(new ArmazemLicencasMemoria(), privateKey, agora);
}

/** Confere o token como o app faz: chave pública + assinatura. */
function lerToken(t: string) {
  const [d, s] = t.split(".");
  expect(verify("sha256", Buffer.from(d!, "base64url"), publicKey, Buffer.from(s!, "base64url"))).toBe(true);
  return JSON.parse(Buffer.from(d!, "base64url").toString());
}

describe("licenças", () => {
  it("ativa por código ou por e-mail + pedido, com token assinado", async () => {
    const l = lic();
    const nova = await l.criar({ plano: "pro", email: "Cliente@Email.com", pedido: "K123", origem: "kiwify" });
    expect(nova.codigo).toMatch(/^ABOT-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/);
    const a = await l.ativar({ codigo: nova.codigo.toLowerCase(), aparelho: "aparelho-1" });
    const t = lerToken(a.token);
    expect(t).toMatchObject({ c: nova.codigo, p: "pro", e: null, a: "aparelho-1" });
    // Pro: 6 meses de atualizações.
    expect(Math.round((t.u - nova.criadoEm) / DIA)).toBeGreaterThanOrEqual(180);
    const b = await l.ativar({ email: "cliente@email.com", pedido: "K123", aparelho: "aparelho-2" });
    expect(b.codigo).toBe(nova.codigo);
    // Pro vale em 2 celulares; o terceiro é recusado.
    await expect(l.ativar({ codigo: nova.codigo, aparelho: "aparelho-3" })).rejects.toMatchObject({ httpStatus: 409 });
    // O mesmo celular pode ativar de novo.
    await expect(l.ativar({ codigo: nova.codigo, aparelho: "aparelho-1" })).resolves.toBeTruthy();
    await expect(l.ativar({ codigo: "ABOT-XXXX-XXXX-XXXX", aparelho: "aparelho-1" })).rejects.toMatchObject({ httpStatus: 404 });
  });

  it("Essencial não tem atualizações; Premium vence se não renovar", async () => {
    let agora = 1_800_000_000_000;
    const l = lic(() => agora);
    const e = await l.criar({ plano: "essencial", email: "a@b.com", origem: "manual" });
    expect(atualizacoesAte(e)).toBe(e.criadoEm);
    const p = await l.criar({ plano: "premium", email: "c@d.com", pedido: "H1", origem: "hotmart", assinatura: "SUB1", pagoAte: agora + 30 * DIA });
    expect(lerToken((await l.ativar({ codigo: p.codigo, aparelho: "aparelho-1" })).token).e).toBe(agora + 33 * DIA);
    agora += 34 * DIA;
    await expect(l.ativar({ codigo: p.codigo, aparelho: "aparelho-1" })).rejects.toMatchObject({ httpStatus: 402 });
    await l.renovar("SUB1", agora + 30 * DIA);
    await expect(l.ativar({ codigo: p.codigo, aparelho: "aparelho-1" })).resolves.toBeTruthy();
    await l.desativar({ origem: "hotmart", pedido: "H1" }, "Reembolso");
    await expect(l.ativar({ codigo: p.codigo, aparelho: "aparelho-1" })).rejects.toMatchObject({ httpStatus: 403 });
  });

  it("lê os webhooks da Kiwify e da Hotmart", () => {
    const agora = 1_800_000_000_000;
    expect(planoDoProduto("abc:premium", "abc", "x")).toBe("premium");
    expect(planoDoProduto(undefined, "z", "Afiliados.bot Pro")).toBe("pro");
    expect(eventoKiwify({ order_id: "O1", order_status: "paid", Product: { product_id: "p1", product_name: "Afiliados.bot Essencial" }, Customer: { email: "x@y.com" } }, undefined, agora))
      .toMatchObject({ tipo: "compra", plano: "essencial", pedido: "O1", email: "x@y.com", pagoAte: null });
    expect(eventoKiwify({ order_id: "O1", order_status: "refunded", webhook_event_type: "order_refunded" }, undefined, agora)).toMatchObject({ tipo: "reembolso", pedido: "O1" });
    expect(eventoKiwify({ order_id: "O2", order_status: "waiting_payment", Product: { product_name: "Pro" } }, undefined, agora).tipo).toBe("ignorar");
    expect(eventoKiwify({ webhook_event_type: "subscription_renewed", order_status: "paid", subscription_id: "S1", Subscription: { next_payment: "2027-03-01" } }, undefined, agora))
      .toMatchObject({ tipo: "renovacao", assinatura: "S1" });
    const hot = (event: string, rec = 1) => ({ event, data: { product: { id: 9, name: "Afiliados.bot Premium" }, buyer: { email: "h@o.com" }, purchase: { transaction: "HP1", recurrence_number: rec }, subscription: { subscriber: { code: "SC1" } } } });
    expect(eventoHotmart(hot("PURCHASE_APPROVED"), undefined, agora)).toMatchObject({ tipo: "compra", plano: "premium", pedido: "HP1", assinatura: "SC1" });
    expect(eventoHotmart(hot("PURCHASE_APPROVED", 2), undefined, agora)).toMatchObject({ tipo: "renovacao", assinatura: "SC1" });
    expect(eventoHotmart(hot("SUBSCRIPTION_CANCELLATION"), undefined, agora)).toMatchObject({ tipo: "cancelamento" });
    expect(eventoHotmart(hot("PURCHASE_CHARGEBACK"), undefined, agora)).toMatchObject({ tipo: "reembolso", motivo: "Chargeback" });
  });
});

describe("rotas de venda", () => {
  async function montar() {
    const dir = mkdtempSync(join(tmpdir(), "rel-"));
    writeFileSync(join(dir, "versoes.json"), JSON.stringify({ versoes: [
      { versionCode: 12, versao: "1.0.0", data: 1_800_000_000_000, arquivo: "afiliados-bot-1.0.0.apk", novidades: ["Licenças"] },
      { versionCode: 13, versao: "1.1.0", data: 1_900_000_000_000, arquivo: "afiliados-bot-1.1.0.apk", novidades: ["Novo"] },
    ] }));
    writeFileSync(join(dir, "afiliados-bot-1.0.0.apk"), "APK10");
    writeFileSync(join(dir, "afiliados-bot-1.1.0.apk"), "APK11");
    const cfg = cfgTeste({ ADMIN_TOKEN: ADMIN, KIWIFY_TOKEN: "segredo-kiwify", HOTMART_HOTTOK: "hottok", RELEASES_DIR: dir });
    const deps = criarDeps(cfg);
    deps.licencas = lic(Date.now);
    return { app: await criarApp(cfg, deps), deps };
  }

  it("site, versão e download são abertos; o resto do backend continua fechado", async () => {
    const { app } = await montar();
    expect((await app.inject({ url: "/site" })).body).toContain("Planos");
    expect((await app.inject({ url: "/api/app/versao" })).json()).toMatchObject({ versionCode: 13, url: "/baixar" });
    expect((await app.inject({ url: "/baixar" })).body).toBe("APK11");
    // Essencial: baixa a versão que o plano dele cobre.
    expect((await app.inject({ url: "/baixar?ate=1850000000000" })).body).toBe("APK10");
    expect((await app.inject({ url: "/api/fila/status" })).statusCode).toBe(401);
    expect((await app.inject({ url: "/api/licenca/../fila/status" })).statusCode).not.toBe(200);
  });

  it("webhook da Kiwify só com assinatura certa; compra vira licença ativável", async () => {
    const { app } = await montar();
    const corpo = JSON.stringify({ order_id: "KW-1", order_status: "paid", Product: { product_id: "p", product_name: "Afiliados.bot Pro" }, Customer: { email: "cli@x.com" } });
    const errado = await app.inject({ method: "POST", url: "/api/webhook/kiwify?signature=abc", headers: { "content-type": "application/json" }, payload: corpo });
    expect(errado.statusCode).toBe(401);
    const ok = await app.inject({ method: "POST", url: `/api/webhook/kiwify?signature=${hmacSha1Hex(corpo, "segredo-kiwify")}`, headers: { "content-type": "application/json" }, payload: corpo });
    expect(ok.json()).toMatchObject({ ok: true });
    const at = await app.inject({ method: "POST", url: "/api/licenca/ativar", payload: { email: "CLI@x.com", pedido: "KW-1", aparelho: "celular-teste-1" } });
    expect(at.json()).toMatchObject({ plano: "pro", nomePlano: "Pro" });
  });

  it("Hotmart exige o hottok; painel exige o token do dono", async () => {
    const { app } = await montar();
    const hot = { event: "PURCHASE_APPROVED", data: { product: { id: 1, name: "Essencial" }, buyer: { email: "h@x.com" }, purchase: { transaction: "HP9" } } };
    expect((await app.inject({ method: "POST", url: "/api/webhook/hotmart", payload: hot })).statusCode).toBe(401);
    expect((await app.inject({ method: "POST", url: "/api/webhook/hotmart", headers: { "x-hotmart-hottok": "hottok" }, payload: hot })).json()).toMatchObject({ ok: true });
    expect((await app.inject({ url: "/api/admin/licencas" })).statusCode).toBe(401);
    const criada = await app.inject({ method: "POST", url: "/api/admin/licencas", headers: { authorization: `Bearer ${ADMIN}` }, payload: { plano: "premium", email: "pix@x.com", meses: 2 } });
    expect(criada.json()).toMatchObject({ plano: "premium", origem: "manual" });
    const lista = await app.inject({ url: "/api/admin/licencas", headers: { authorization: `Bearer ${ADMIN}` } });
    expect(lista.json().licencas).toHaveLength(2);
  });
});

describe("servidor do cliente exige app licenciado", () => {
  it("sem token, token falso ou vencido: 402; token do dono: passa", async () => {
    const { conferidorDeLicenca } = await import("../src/licencaApp.js");
    const l = lic(() => 1_800_000_000_000);
    const nova = await l.criar({ plano: "premium", email: "a@b.com", origem: "manual", pagoAte: 1_800_000_000_000 + 30 * DIA });
    const { token } = await l.ativar({ codigo: nova.codigo, aparelho: "aparelho-1" });
    let agora = 1_800_000_000_000;
    const conferir = conferidorDeLicenca(publicKey, () => agora);
    expect(conferir(token)).toEqual({ ok: true, plano: "premium" });
    expect(conferir(undefined).ok).toBe(false);
    expect(conferir(token.slice(0, -4) + "AAAA").ok).toBe(false);
    agora += 40 * DIA;
    expect(conferir(token)).toMatchObject({ ok: false, motivo: expect.stringContaining("vencida") });

    const cfg = cfgTeste({ LICENCA_LIVRE: undefined });
    const app = await criarApp(cfg, criarDeps(cfg));
    const r = await app.inject({ url: "/api/fila/status", headers: { authorization: `Bearer ${cfg.APP_TOKEN}` } });
    expect(r.statusCode).toBe(402);
    expect((await app.inject({ url: "/api/health" })).statusCode).toBe(200);
  });
});
