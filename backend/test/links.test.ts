import { describe, expect, it } from "vitest";
import { analisarLink, extrairIds, pareceAfiliado, validarUrl } from "../src/lib/links.js";
import { fakeFetch } from "./helpers.js";

describe("validarUrl", () => {
  it("aceita Shopee e Mercado Livre em HTTPS", () => {
    expect(validarUrl("https://shopee.com.br/Fone-i.123.456").hostname).toBe("shopee.com.br");
    expect(validarUrl(" https://produto.mercadolivre.com.br/MLB-123456789-fone ").hostname).toBe("produto.mercadolivre.com.br");
  });
  it("recusa HTTP, domínio estranho, usuário embutido e porta", () => {
    expect(() => validarUrl("http://shopee.com.br/x")).toThrow(/HTTPS/);
    expect(() => validarUrl("https://shopee.com.br.golpe.xyz/x")).toThrow(/Domínio/);
    expect(() => validarUrl("https://user:pw@shopee.com.br/x")).toThrow(/usuário/);
    expect(() => validarUrl("https://shopee.com.br:8443/x")).toThrow(/Porta/);
    expect(() => validarUrl("não é link")).toThrow();
  });
});

describe("extrairIds", () => {
  it("Shopee nos dois formatos", () => {
    expect(extrairIds(new URL("https://shopee.com.br/Fone-Bluetooth-i.111.222?sp_atk=1"))).toEqual({ shopee: { shopId: "111", itemId: "222" } });
    expect(extrairIds(new URL("https://shopee.com.br/product/333/444"))).toEqual({ shopee: { shopId: "333", itemId: "444" } });
  });
  it("Mercado Livre com e sem hífen", () => {
    expect(extrairIds(new URL("https://produto.mercadolivre.com.br/MLB-1234567890-fone-_JM"))).toEqual({ mercadolivre: { itemId: "MLB1234567890" } });
    expect(extrairIds(new URL("https://www.mercadolivre.com.br/fone/p/MLB19876543"))).toEqual({ mercadolivre: { itemId: "MLB19876543" } });
  });
  it("indício de afiliado não é confirmação, mas é detectado", () => {
    expect(pareceAfiliado(new URL("https://s.shopee.com.br/abc"))).toBe(true);
    expect(pareceAfiliado(new URL("https://meli.la/xyz"))).toBe(true);
    expect(pareceAfiliado(new URL("https://shopee.com.br/Fone-i.1.2"))).toBe(false);
  });
});

describe("analisarLink", () => {
  it("segue link curto até o produto", async () => {
    const { f } = fakeFetch((url) =>
      url.startsWith("https://s.shopee.com.br") ? new Response(null, { status: 302, headers: { location: "https://shopee.com.br/Fone-i.9.8" } }) : new Response("ok"),
    );
    const a = await analisarLink("https://s.shopee.com.br/abc", f);
    expect(a.ids.shopee).toEqual({ shopId: "9", itemId: "8" });
    expect(a.pareceAfiliado).toBe(true);
    expect(a.redirecionamentos).toHaveLength(1);
  });
  it("bloqueia redirecionamento para outro domínio", async () => {
    const { f } = fakeFetch(() => new Response(null, { status: 301, headers: { location: "https://golpe.example/login" } }));
    const a = await analisarLink("https://meli.la/abc", f);
    expect(a.urlFinal).toBe("https://meli.la/abc");
    expect(a.avisos.join(" ")).toMatch(/bloqueado/);
  });
  it("não acessa a rede quando o link já tem o produto", async () => {
    const { f, chamadas } = fakeFetch(() => new Response("x"));
    await analisarLink("https://shopee.com.br/Fone-i.1.2", f);
    expect(chamadas).toHaveLength(0);
  });
});
