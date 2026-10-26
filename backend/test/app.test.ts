import { describe, expect, it } from "vitest";
import { criarApp, criarDeps } from "../src/app.js";
import { Telegram } from "../src/integrations/telegram.js";
import { cfgTeste, fakeFetch, json, TOKEN } from "./helpers.js";

const auth = { authorization: `Bearer ${TOKEN}` };

async function app(extra = {}, telegram?: Telegram) {
  const cfg = cfgTeste(extra);
  const deps = criarDeps(cfg);
  if (telegram) deps.telegram = telegram;
  return criarApp(cfg, deps);
}

describe("acesso", () => {
  it("health é aberto; o resto exige token", async () => {
    const a = await app();
    expect((await a.inject({ url: "/api/health" })).statusCode).toBe(200);
    expect((await a.inject({ url: "/api/integrations/status" })).statusCode).toBe(401);
    expect((await a.inject({ url: "/api/integrations/status", headers: { authorization: "Bearer errado" } })).statusCode).toBe(401);
    expect((await a.inject({ url: "/api/integrations/status", headers: auth })).statusCode).toBe(200);
  });

  it("valida a entrada e recusa corpo grande", async () => {
    const a = await app();
    const r = await a.inject({ method: "POST", url: "/api/copy/generate", headers: auth, payload: { nome: "" } });
    expect(r.statusCode).toBe(400);
    expect(r.json().erro).toBe("dados_invalidos");
    const grande = await a.inject({ method: "POST", url: "/api/links/analyze", headers: { ...auth, "content-type": "application/json" }, payload: JSON.stringify({ url: "x".repeat(40_000) }) });
    expect(grande.statusCode).toBe(413);
  });

  it("rota de print aceita imagem grande e recusa arquivo que não é imagem", async () => {
    const a = await app();
    const grande = { imagens: [{ tipo: "image/jpeg", base64: "A".repeat(3_000_000) }] };
    const r = await a.inject({ method: "POST", url: "/api/product/from-images", headers: auth, payload: grande });
    expect(r.statusCode).toBe(503); // passou do limite de tamanho; só falta a chave da IA
    const dez = { imagens: Array.from({ length: 10 }, () => ({ tipo: "image/jpeg", base64: "A".repeat(700_000) })) };
    expect((await a.inject({ method: "POST", url: "/api/product/from-images", headers: auth, payload: dez })).statusCode).toBe(503);
    const onze = { imagens: Array.from({ length: 11 }, () => ({ tipo: "image/jpeg", base64: "A".repeat(200) })) };
    expect((await a.inject({ method: "POST", url: "/api/product/from-images", headers: auth, payload: onze })).statusCode).toBe(400);
    const ruim = await a.inject({ method: "POST", url: "/api/product/from-images", headers: auth, payload: { imagens: [{ tipo: "application/pdf", base64: "A".repeat(200) }] } });
    expect(ruim.statusCode).toBe(400);
  });

  it("link de domínio estranho dá erro claro", async () => {
    const a = await app();
    const r = await a.inject({ method: "POST", url: "/api/links/analyze", headers: auth, payload: { url: "https://golpe.example/x" } });
    expect(r.statusCode).toBe(400);
    expect(r.json().erro).toBe("link_invalido");
  });
});

describe("status das integrações", () => {
  it("sem credenciais tudo aparece como não configurado; WhatsApp automático indisponível", async () => {
    const a = await app();
    const lista = (await a.inject({ url: "/api/integrations/status", headers: auth })).json().integracoes as { id: string; estado: string }[];
    const est = Object.fromEntries(lista.map((i) => [i.id, i.estado]));
    expect(est).toEqual({ shopee: "nao_configurado", mercadolivre: "nao_configurado", ia: "nao_configurado", telegram: "nao_configurado", whatsapp: "indisponivel" });
  });
});

describe("Telegram", () => {
  it("a mesma chave nunca publica duas vezes", async () => {
    let envios = 0;
    const { f } = fakeFetch(() => {
      envios++;
      return json({ ok: true, result: { message_id: 77, chat: { id: -100123 } } });
    });
    const cfg = cfgTeste();
    const tg = new Telegram({ token: "123:abc", dataDir: cfg.DATA_DIR }, f);
    const a = await app({}, tg);
    const corpo = { chave: "fila-1-abcdef", chatId: "@meucanal", texto: "Oferta" };
    const r1 = (await a.inject({ method: "POST", url: "/api/publish/telegram", headers: auth, payload: corpo })).json();
    const r2 = (await a.inject({ method: "POST", url: "/api/publish/telegram", headers: auth, payload: corpo })).json();
    expect(r1).toMatchObject({ messageId: 77, repetido: false });
    expect(r2).toMatchObject({ messageId: 77, repetido: true });
    expect(envios).toBe(1);
    // Depois de reiniciar o backend, continua sem repetir.
    const tg2 = new Telegram({ token: "123:abc", dataDir: cfg.DATA_DIR }, f);
    expect((await tg2.publicar(corpo)).repetido).toBe(true);
    expect(envios).toBe(1);
  });

  it("legenda longa vira mensagem de texto, sem cortar o link", async () => {
    const { f, chamadas } = fakeFetch(() => json({ ok: true, result: { message_id: 1, chat: { id: -1 } } }));
    const tg = new Telegram({ token: "123:abc", dataDir: cfgTeste().DATA_DIR }, f);
    const texto = "x".repeat(1100) + "\n\nhttps://s.shopee.com.br/abc";
    await tg.publicar({ chave: "fila-3-abcdef", chatId: "@meucanal", texto, imagem: "https://cf.shopee.com.br/a.jpg" });
    expect(chamadas[0]!.url).toMatch(/sendMessage$/);
    expect(JSON.parse(String(chamadas[0]!.init.body)).text.endsWith("https://s.shopee.com.br/abc")).toBe(true);
  });

  it("falha do Telegram não vira 'publicado'", async () => {
    const { f } = fakeFetch(() => json({ ok: false, description: "Forbidden: bot is not a member" }, 403));
    const tg = new Telegram({ token: "123:abc", dataDir: cfgTeste().DATA_DIR }, f);
    const a = await app({}, tg);
    const r = await a.inject({ method: "POST", url: "/api/publish/telegram", headers: auth, payload: { chave: "fila-2-abcdef", chatId: "@meucanal", texto: "x" } });
    expect(r.statusCode).toBe(400);
    expect(r.json().erro).toBe("sem_acesso");
  });
});
