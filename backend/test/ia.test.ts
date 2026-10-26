import type Anthropic from "@anthropic-ai/sdk";
import { describe, expect, it } from "vitest";
import { GeradorTexto, type TextosGerados } from "../src/integrations/ai.js";
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
