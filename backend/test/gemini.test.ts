import { describe, expect, it } from "vitest";
import { GeradorTexto } from "../src/integrations/ai.js";
import { Gemini } from "../src/integrations/gemini.js";
import { fakeFetch, json } from "./helpers.js";

const resposta = (obj: unknown) => json({ candidates: [{ content: { parts: [{ text: JSON.stringify(obj) }] } }] });
const print = {
  marketplace: "mercadolivre", nome: "Torneira Gourmet Preta", preco: 34.94, precoAnterior: 60.25, comissaoPercentual: 17,
  caracteristicas: ["Cor e Acabamento: Preto | Parede", "Frete grátis acima de R$ 19"], descricao: null, avaliacao: 4.8, urgencia: null, foto: { imagem: 0, caixa: [40, 0, 460, 1000] }, avisos: [],
};

describe("Gemini (grátis)", () => {
  it("sem chave da Anthropic, lê o print pelo Gemini e manda a imagem", async () => {
    const { f, chamadas } = fakeFetch(() => resposta(print));
    const ia = new GeradorTexto({ modelo: "m", limiteDiario: 5 }, undefined, new Gemini({ apiKey: "AIza-teste", modelos: ["gemini-flash-lite-latest"] }, f));
    expect(ia.provedor).toBe("gemini");
    const r = await ia.lerPrint({ imagens: [{ tipo: "image/jpeg", base64: "A".repeat(200) }] });
    expect(r.dados).toMatchObject({ preco: 34.94, precoAnterior: 60.25, comissaoPercentual: 17 });
    expect(chamadas[0]!.url).toContain("/models/gemini-flash-lite-latest:generateContent");
    expect((chamadas[0]!.init.headers as Record<string, string>)["x-goog-api-key"]).toBe("AIza-teste");
    const corpo = JSON.parse(String(chamadas[0]!.init.body));
    expect(corpo.contents[0].parts[0].inlineData.mimeType).toBe("image/jpeg");
    expect(ia.status().detalhe).toMatch(/Gemini grátis/);
  });

  it("limite do plano grátis (429) passa para o próximo modelo", async () => {
    const { f, chamadas } = fakeFetch((url) => (url.includes("lite") ? json({ error: { status: "RESOURCE_EXHAUSTED" } }, 429) : resposta(print)));
    const g = new Gemini({ apiKey: "k", modelos: ["gemini-flash-lite-latest", "gemini-flash-latest"] }, f);
    const ia = new GeradorTexto({ modelo: "m", limiteDiario: 5 }, undefined, g);
    await ia.lerPrint({ imagens: [{ tipo: "image/png", base64: "A".repeat(200) }] });
    expect(chamadas.map((c) => c.url.split("/models/")[1])).toEqual(["gemini-flash-lite-latest:generateContent", "gemini-flash-latest:generateContent"]);
  });

  it("JSON fora do formato não vira dado", async () => {
    const { f } = fakeFetch(() => resposta({ nome: 123 }));
    const g = new Gemini({ apiKey: "k", modelos: ["m"] }, f);
    const ia = new GeradorTexto({ modelo: "m", limiteDiario: 5 }, undefined, g);
    await expect(ia.lerPrint({ imagens: [{ tipo: "image/png", base64: "A".repeat(200) }] })).rejects.toMatchObject({ codigo: "resposta_invalida" });
  });

  it("chave errada avisa como erro de autenticação", async () => {
    const { f } = fakeFetch(() => json({ error: { message: "API key not valid. Please pass a valid API key." } }, 400));
    const g = new Gemini({ apiKey: "k", modelos: ["m"] }, f);
    await expect(g.json((await import("zod/v4")).z.object({}), "s", [{ text: "x" }])).rejects.toMatchObject({ codigo: "autenticacao" });
  });

  it("os textos também passam pela conferência quando vêm do Gemini", async () => {
    const textos = { titulo: "Torneira", whatsapp: "Torneira por R$ 19,90, a mais barata!", canal: "x", descricao: "x", cta: "x", objetiva: "x", chamativa: "x", hashtags: [], urgencia: null };
    const certo = { ...textos, whatsapp: "Torneira por R$ 34,94" };
    let n = 0;
    const { f } = fakeFetch(() => resposta(n++ === 0 ? textos : certo));
    const ia = new GeradorTexto({ modelo: "m", limiteDiario: 5 }, undefined, new Gemini({ apiKey: "k", modelos: ["m"] }, f));
    const r = await ia.gerar({ nome: "Torneira", marketplace: "mercadolivre", preco: 34.94, caracteristicas: [], canal: "whatsapp" } as never);
    expect(n).toBe(2);
    expect(r.avisos).toEqual([]);
    expect(r.modelo).toBe("gemini");
  });
});
