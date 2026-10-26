import { createHash } from "node:crypto";
import { describe, expect, it } from "vitest";
import { MercadoLivre } from "../src/integrations/mercadolivre.js";
import { assinar, Shopee } from "../src/integrations/shopee.js";
import { cfgTeste, fakeFetch, json } from "./helpers.js";

const ENDPOINT = "https://open-api.affiliate.shopee.com.br/graphql";

describe("Shopee", () => {
  it("assina no formato da Open API", () => {
    const sig = createHash("sha256").update("1231700000000{}abc").digest("hex");
    expect(assinar("123", "abc", 1700000000, "{}")).toBe(`SHA256 Credential=123, Timestamp=1700000000, Signature=${sig}`);
  });

  it("sem credenciais não chama a rede", async () => {
    const { f, chamadas } = fakeFetch(() => json({}));
    const s = new Shopee({ endpoint: ENDPOINT }, f);
    await expect(s.produto("1", "2")).rejects.toMatchObject({ codigo: "nao_configurado" });
    expect(chamadas).toHaveLength(0);
    expect((await s.status(true)).estado).toBe("nao_configurado");
  });

  it("conta sem acesso vira 'aguardando autorização'", async () => {
    const { f } = fakeFetch(() => json({ errors: [{ message: "x", extensions: { code: 10035 } }] }));
    const s = new Shopee({ appId: "1", secret: "s", endpoint: ENDPOINT }, f);
    expect((await s.status(true)).estado).toBe("aguardando_autorizacao");
    await expect(s.produto("1", "2")).rejects.toMatchObject({ codigo: "sem_acesso" });
  });

  it("mapeia o produto e converte a comissão para porcentagem", async () => {
    const { f, chamadas } = fakeFetch(() =>
      json({ data: { productOfferV2: { nodes: [{ itemId: 2, shopId: 1, productName: "Fone", priceMin: "59.9", commissionRate: "0.08", offerLink: "https://s.shopee.com.br/x" }] } } }),
    );
    const s = new Shopee({ appId: "1", secret: "s", endpoint: ENDPOINT }, f, () => 1700000000);
    const p = await s.produto("1", "2");
    expect(p).toMatchObject({ nome: "Fone", preco: 59.9, comissaoPercentual: 8, linkAfiliado: "https://s.shopee.com.br/x", avaliacao: null, vendas: null });
    expect(String((chamadas[0]!.init.headers as Record<string, string>).authorization)).toMatch(/^SHA256 Credential=1, Timestamp=1700000000/);
  });

  it("link curto só é aceito se a Shopee devolver um link HTTPS", async () => {
    const { f } = fakeFetch(() => json({ data: { generateShortLink: { shortLink: "" } } }));
    const s = new Shopee({ appId: "1", secret: "s", endpoint: ENDPOINT }, f);
    await expect(s.linkCurto("https://shopee.com.br/x-i.1.2")).rejects.toMatchObject({ codigo: "resposta_invalida" });
  });
});

describe("Mercado Livre", () => {
  it("renova o token, guarda o novo refresh e tenta de novo após 401", async () => {
    let tokens = 0;
    let itens = 0;
    const { f } = fakeFetch((url) => {
      if (url.endsWith("/oauth/token")) {
        tokens++;
        return json({ access_token: `A${tokens}`, refresh_token: `R${tokens}`, expires_in: 21600 });
      }
      itens++;
      if (itens === 1) return json({ message: "expirado" }, 401);
      return json({ id: "MLB123456789", title: "Fone", price: 99.9, original_price: 149.9, status: "active", pictures: [{ secure_url: "https://http2.mlstatic.com/a.jpg" }] });
    });
    const ml = new MercadoLivre({ clientId: "c", clientSecret: "s", refreshToken: "R0", dataDir: cfgTeste().DATA_DIR }, f);
    const p = await ml.produto("MLB123456789");
    expect(p).toMatchObject({ nome: "Fone", preco: 99.9, precoAnterior: 149.9, linkAfiliado: null, comissaoPercentual: null });
    expect(tokens).toBe(2);
  });

  it("declara que link de afiliado não tem API", () => {
    const ml = new MercadoLivre({ dataDir: "x" });
    const op = ml.status().operacoes.find((o) => o.id === "link_afiliado")!;
    expect(op.implementada).toBe(false);
    expect(op.disponivel).toBe(false);
  });

  it("recusa código inválido sem chamar a rede", async () => {
    const { f, chamadas } = fakeFetch(() => json({}));
    const ml = new MercadoLivre({ clientId: "c", clientSecret: "s", refreshToken: "r", dataDir: "x" }, f);
    await expect(ml.produto("ABC")).rejects.toMatchObject({ codigo: "nao_encontrado" });
    expect(chamadas).toHaveLength(0);
  });
});
