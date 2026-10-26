import type Anthropic from "@anthropic-ai/sdk";
import { describe, expect, it } from "vitest";
import { GeradorTexto, limparPrint, type TextosGerados } from "../src/integrations/ai.js";
import { conferirTexto } from "../src/lib/copyGuard.js";

const base: TextosGerados = {
  titulo: "Fone Bluetooth com cancelamento de ruído",
  whatsapp: "🎧 Fone Bluetooth por R$ 59,90",
  canal: "Fone Bluetooth por R$ 59,90",
  descricao: "Bateria de 30 horas.",
  cta: "Confira no link",
  objetiva: "Fone Bluetooth, R$ 59,90",
  chamativa: "Seu som sem fio por R$ 59,90!",
  hashtags: ["fone", "#bluetooth"],
  urgencia: null,
};

function clienteFalso(respostas: Partial<TextosGerados>[]) {
  const pedidos: unknown[] = [];
  const client = {
    beta: {
      messages: {
        parse: async (p: unknown) => {
          pedidos.push(p);
          const r = respostas[Math.min(pedidos.length - 1, respostas.length - 1)];
          return { stop_reason: "end_turn", parsed_output: { ...base, ...r } };
        },
      },
    },
  } as unknown as Anthropic;
  return { client, pedidos };
}

const produto = { nome: "Fone Bluetooth", marketplace: "shopee" as const, preco: 59.9, caracteristicas: ["Bateria de 30 horas"], canal: "whatsapp" as const };

describe("conferirTexto", () => {
  it("bloqueia preço inventado, desconto sem prova, link e 'mais barato'", () => {
    const f = { nome: "Fone", preco: 59.9 };
    expect(conferirTexto("Por R$ 49,90", f).join()).toMatch(/sem base/);
    expect(conferirTexto("Com 40% de desconto", f).join()).toMatch(/porcentagem|sem base/);
    expect(conferirTexto("Compre em https://x.com", f).join()).toMatch(/link/);
    expect(conferirTexto("O mais barato do Brasil", f).join()).toMatch(/mais barato/);
    expect(conferirTexto("Últimas unidades!", f).join()).toMatch(/escassez/);
  });
  it("aceita os valores reais e a urgência que tem fato", () => {
    const f = { nome: "Fone", preco: 59.9, precoAnterior: 99.9, descontoPercentual: 40, urgenciaFato: "Oferta válida até 26/10 23:59" };
    expect(conferirTexto("De R$ 99,90 por R$ 59,90 (40% off). Só hoje!", f)).toEqual([]);
  });
});

describe("GeradorTexto", () => {
  it("sem chave avisa que não está configurada", async () => {
    const g = new GeradorTexto({ modelo: "m", limiteDiario: 5 });
    await expect(g.gerar(produto as never)).rejects.toMatchObject({ codigo: "nao_configurado" });
  });

  it("pede correção quando a IA inventa e devolve o texto limpo", async () => {
    const { client, pedidos } = clienteFalso([{ whatsapp: "Fone por R$ 39,90, o mais barato!" }, {}]);
    const g = new GeradorTexto({ modelo: "m", limiteDiario: 5 }, client);
    const r = await g.gerar(produto as never);
    expect(pedidos).toHaveLength(2);
    expect(r.avisos).toEqual([]);
    expect(r.textos.hashtags).toEqual(["#fone", "#bluetooth"]);
  });

  it("se continuar errado, devolve com avisos para eu revisar", async () => {
    const { client } = clienteFalso([{ chamativa: "Estoque limitado!" }]);
    const g = new GeradorTexto({ modelo: "m", limiteDiario: 5 }, client);
    const r = await g.gerar(produto as never);
    expect(r.avisos.join()).toMatch(/escassez/);
  });

  it("zera a urgência quando não há fato", async () => {
    const { client } = clienteFalso([{ urgencia: "Corre!" }]);
    const g = new GeradorTexto({ modelo: "m", limiteDiario: 5 }, client);
    expect((await g.gerar(produto as never)).textos.urgencia).toBeNull();
  });

  it("respeita o limite diário", async () => {
    const { client } = clienteFalso([{}]);
    const g = new GeradorTexto({ modelo: "m", limiteDiario: 1 }, client);
    await g.gerar(produto as never);
    await expect(g.gerar(produto as never)).rejects.toMatchObject({ codigo: "limite" });
  });
});

describe("leitura de print", () => {
  const img = { tipo: "image/jpeg" as const, base64: "A".repeat(200) };
  const dados = {
    marketplace: "mercadolivre", nome: " Fone Bluetooth ", preco: 59.9, precoAnterior: 99.9, comissaoPercentual: 17,
    caracteristicas: ["Bluetooth 5.3", ""], descricao: null, avaliacao: 4.8, urgencia: null, nicho: null, cupom: null, foto: null, avisos: [],
  };
  function cliente(saida: Record<string, unknown>) {
    const pedidos: any[] = [];
    const client = { beta: { messages: { parse: async (p: unknown) => { pedidos.push(p); return { stop_reason: "end_turn", parsed_output: saida }; } } } } as unknown as Anthropic;
    return { client, pedidos };
  }

  it("lê o print e manda as imagens para a IA", async () => {
    const { client, pedidos } = cliente(dados);
    const r = await new GeradorTexto({ modelo: "m", limiteDiario: 5 }, client).lerPrint({ imagens: [img] });
    expect(r.dados).toMatchObject({ nome: "Fone Bluetooth", preco: 59.9, precoAnterior: 99.9, comissaoPercentual: 17, caracteristicas: ["Bluetooth 5.3"] });
    expect(pedidos[0].messages[0].content[0]).toMatchObject({ type: "image", source: { type: "base64", media_type: "image/jpeg" } });
  });

  it("descarta valores impossíveis em vez de inventar", () => {
    const d = limparPrint({ ...dados, preco: 99.9, precoAnterior: 59.9, comissaoPercentual: 170, avaliacao: 9 } as never);
    expect(d.precoAnterior).toBeNull();
    expect(d.comissaoPercentual).toBeNull();
    expect(d.avaliacao).toBeNull();
    expect(d.avisos.length).toBeGreaterThanOrEqual(2);
  });

  it("foto: aceita retângulo válido e descarta o impossível", () => {
    const ok = limparPrint({ ...dados, foto: { imagem: 0, caixa: [40, 0, 460, 1000] } } as never, 1);
    expect(ok.foto).toEqual({ imagem: 0, caixa: [40, 0, 460, 1000] });
    const foraDaLista = limparPrint({ ...dados, foto: { imagem: 2, caixa: [40, 0, 460, 1000] } } as never, 1);
    expect(foraDaLista.foto).toBeNull();
    const pequena = limparPrint({ ...dados, foto: { imagem: 0, caixa: [10, 10, 30, 30] } } as never, 1);
    expect(pequena.foto).toBeNull();
    expect(pequena.avisos.join()).toMatch(/foto/);
  });

  it("sem chave da IA avisa", async () => {
    await expect(new GeradorTexto({ modelo: "m", limiteDiario: 5 }).lerPrint({ imagens: [img] })).rejects.toMatchObject({ codigo: "nao_configurado" });
  });
});
