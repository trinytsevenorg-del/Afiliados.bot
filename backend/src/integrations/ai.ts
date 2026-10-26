// Geração de textos promocionais com a API da Anthropic (chave só no backend).
import Anthropic from "@anthropic-ai/sdk";
import { betaZodOutputFormat } from "@anthropic-ai/sdk/helpers/beta/zod";
import { z } from "zod/v4";
import { conferirTexto, type FatosProduto } from "../lib/copyGuard.js";
import { IntegracaoErro, type StatusIntegracao } from "./types.js";

export const PedidoTexto = z.object({
  nome: z.string().trim().min(2).max(200),
  marketplace: z.enum(["shopee", "mercadolivre", "outro"]),
  preco: z.number().positive().max(1_000_000).nullish(),
  precoAnterior: z.number().positive().max(1_000_000).nullish(),
  descontoPercentual: z.number().min(1).max(95).nullish(),
  caracteristicas: z.array(z.string().trim().min(1).max(200)).max(15).default([]),
  descricaoOriginal: z.string().max(2000).nullish(),
  nicho: z.string().max(60).nullish(),
  preferenciasTexto: z.string().max(400).nullish(),
  urgenciaFato: z.string().max(200).nullish(),
  canal: z.enum(["whatsapp", "telegram", "geral"]).default("geral"),
  versaoAnterior: z.string().max(3000).nullish(),
});
export type PedidoTexto = z.infer<typeof PedidoTexto>;

export const TextosGerados = z.object({
  titulo: z.string().describe("Título promocional curto, até 70 caracteres"),
  whatsapp: z.string().describe("Mensagem curta para WhatsApp, com emojis moderados, sem link"),
  canal: z.string().describe("Texto para canal de ofertas (Telegram), sem link"),
  descricao: z.string().describe("Descrição um pouco mais completa, só com fatos fornecidos"),
  cta: z.string().describe("Chamada para ação curta, sem link"),
  objetiva: z.string().describe("Versão objetiva e direta"),
  chamativa: z.string().describe("Versão mais chamativa, sem exageros enganosos"),
  hashtags: z.array(z.string()).describe("De 0 a 5 hashtags úteis, sem o símbolo repetido"),
  urgencia: z.string().nullable().describe("Frase de urgência SOMENTE se urgenciaFato foi fornecido; senão null"),
});
export type TextosGerados = z.infer<typeof TextosGerados>;

const SISTEMA = `Você escreve textos de divulgação de ofertas de afiliado em português do Brasil.
Use apenas os fatos do JSON recebido. Regras que não podem ser quebradas:
- Não invente especificações, descontos, avaliações, número de vendas, estoque, frete, prazo ou garantia.
- Não diga que é o mais barato ou o menor preço.
- Não crie falsa escassez nem urgência. Só use urgência se "urgenciaFato" existir, e repita esse fato com fidelidade.
- Não escreva nenhum link nem domínio. O link é adicionado depois, sem alteração.
- Não prometa ganhos financeiros a quem compra.
- Preços só no formato R$ 0,00 e só os valores recebidos. Porcentagem só se "descontoPercentual" existir.
- Se um dado não foi informado, não fale dele.
- Respeite "preferenciasTexto" do nicho quando existir.
- Se "versaoAnterior" existir, escreva uma versão diferente dela.`;

export interface UsoIa {
  data: string;
  usados: number;
}

export class GeradorTexto {
  private client: Anthropic | null;
  private uso: UsoIa = { data: "", usados: 0 };

  constructor(
    private readonly opts: { apiKey?: string; modelo: string; limiteDiario: number },
    client?: Anthropic,
  ) {
    this.client = client ?? (opts.apiKey ? new Anthropic({ apiKey: opts.apiKey, timeout: 60_000, maxRetries: 2 }) : null);
  }

  get configurada() {
    return this.client !== null;
  }

  usoHoje(): UsoIa {
    const hoje = new Date().toISOString().slice(0, 10);
    if (this.uso.data !== hoje) this.uso = { data: hoje, usados: 0 };
    return this.uso;
  }

  private async chamar(pedido: PedidoTexto, correcao?: string[]): Promise<TextosGerados> {
    const conteudo = JSON.stringify(pedido);
    const extra = correcao?.length ? `\n\nA versão anterior foi recusada por: ${correcao.join("; ")}. Corrija.` : "";
    let resp;
    try {
      resp = await this.client!.beta.messages.parse({
        model: this.opts.modelo,
        max_tokens: 4000,
        betas: ["server-side-fallback-2026-07-01"],
        fallbacks: "default",
        output_config: { effort: "low", format: betaZodOutputFormat(TextosGerados) },
        system: SISTEMA,
        messages: [{ role: "user", content: `Dados do produto:\n${conteudo}${extra}` }],
      });
    } catch (e) {
      if (e instanceof Anthropic.AuthenticationError) throw new IntegracaoErro("autenticacao", "Chave da IA recusada", 401);
      if (e instanceof Anthropic.RateLimitError) throw new IntegracaoErro("limite", "IA ocupada (limite do provedor). Tente em instantes.", 429);
      if (e instanceof Anthropic.APIConnectionTimeoutError) throw new IntegracaoErro("indisponivel", "A IA demorou demais para responder", 504);
      if (e instanceof Anthropic.APIError) throw new IntegracaoErro("indisponivel", `Erro da IA (${e.status ?? "conexão"})`);
      throw e;
    }
    if (resp.stop_reason === "refusal") throw new IntegracaoErro("indisponivel", "A IA recusou gerar este texto. Escreva à mão.", 422);
    if (!resp.parsed_output) throw new IntegracaoErro("resposta_invalida", "A IA não devolveu o formato esperado", 502);
    return resp.parsed_output;
  }

  async gerar(pedido: PedidoTexto) {
    if (!this.client) throw new IntegracaoErro("nao_configurado", "IA não configurada (ANTHROPIC_API_KEY)", 503);
    const uso = this.usoHoje();
    if (uso.usados >= this.opts.limiteDiario) throw new IntegracaoErro("limite", `Limite diário de ${this.opts.limiteDiario} gerações atingido`, 429);
    uso.usados++;

    // Desconto só entra se os dois preços existirem e baterem.
    const fatos: FatosProduto = { ...pedido };
    if (pedido.descontoPercentual && !(pedido.preco && pedido.precoAnterior)) fatos.descontoPercentual = null;
    const limpo: PedidoTexto = { ...pedido, descontoPercentual: fatos.descontoPercentual ?? null, urgenciaFato: pedido.urgenciaFato ?? null };

    let textos = await this.chamar(limpo);
    let problemas = this.conferir(textos, fatos);
    if (problemas.length) {
      textos = await this.chamar(limpo, problemas);
      problemas = this.conferir(textos, fatos);
    }
    if (!fatos.urgenciaFato) textos.urgencia = null;
    textos.hashtags = textos.hashtags.slice(0, 5).map((h) => "#" + h.replace(/^#+/, "").replace(/\s+/g, ""));
    return { textos, avisos: problemas, modelo: this.opts.modelo, geradoEm: new Date().toISOString(), restantesHoje: this.opts.limiteDiario - uso.usados };
  }

  private conferir(t: TextosGerados, f: FatosProduto) {
    const partes = [t.titulo, t.whatsapp, t.canal, t.descricao, t.cta, t.objetiva, t.chamativa, f.urgenciaFato ? "" : t.urgencia ?? ""];
    return [...new Set(partes.flatMap((p) => conferirTexto(p, f)))];
  }

  status(): StatusIntegracao {
    const ok = this.configurada;
    const uso = this.usoHoje();
    return {
      id: "ia",
      nome: "IA de textos (Anthropic)",
      estado: ok ? "configurado" : "nao_configurado",
      detalhe: ok ? `Modelo ${this.opts.modelo}. Usados hoje: ${uso.usados} de ${this.opts.limiteDiario}.` : "Falta ANTHROPIC_API_KEY no backend",
      verificadoEm: new Date().toISOString(),
      comoConfigurar: "Crie uma chave em console.anthropic.com e coloque em ANTHROPIC_API_KEY no .env do backend. Nunca no app.",
      operacoes: [{ id: "gerar_texto", descricao: "Gerar textos promocionais", implementada: true, disponivel: ok, motivo: ok ? undefined : "Não configurado" }],
    };
  }
}
