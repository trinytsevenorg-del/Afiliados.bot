// Geração de textos promocionais com a API da Anthropic (chave só no backend).
import Anthropic from "@anthropic-ai/sdk";
import { betaZodOutputFormat } from "@anthropic-ai/sdk/helpers/beta/zod";
import { z } from "zod/v4";
import { conferirTexto, type FatosProduto } from "../lib/copyGuard.js";
import { Gemini } from "./gemini.js";
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

export const PedidoPrint = z.object({
  imagens: z
    .array(
      z.object({
        tipo: z.enum(["image/jpeg", "image/png", "image/webp"]),
        // ~4 MB por imagem depois de virar base64
        base64: z.string().min(100).max(5_600_000).regex(/^[A-Za-z0-9+/=]+$/, "Imagem inválida"),
      }),
    )
    .min(1)
    .max(10),
});
export type PedidoPrint = z.infer<typeof PedidoPrint>;

export const DadosPrint = z.object({
  marketplace: z.enum(["shopee", "mercadolivre", "outro", "desconhecido"]),
  nome: z.string().nullable().describe("Título do produto exatamente como aparece"),
  preco: z.number().nullable().describe("Preço atual à vista em reais, como número (ex.: 59.9). Null se não estiver visível"),
  precoAnterior: z.number().nullable().describe("Preço riscado/anterior em reais, só se aparecer riscado na tela"),
  comissaoPercentual: z.number().nullable().describe("Porcentagem de ganho/comissão do afiliado mostrada na tela (ex.: 'Ganhe 17%' → 17)"),
  caracteristicas: z.array(z.string()).describe("Até 15 características escritas nas telas: especificações, cor, tamanho, medidas, material, selos como 'Mais vendido', '+10 mil vendidos', 'Frete grátis acima de R$ 19', 'Loja oficial X'. Nada deduzido"),
  descricao: z.string().nullable().describe("Trecho da descrição visível, se houver"),
  avaliacao: z.number().nullable().describe("Nota média visível (ex.: 4.8), se houver"),
  urgencia: z.string().nullable().describe("Prazo de oferta escrito na tela (ex.: 'termina em 2h'), se houver"),
  foto: z
    .object({
      imagem: z.number().int().describe("Índice (começando em 0) da imagem onde está a foto principal do produto"),
      caixa: z.array(z.number()).describe("Retângulo da foto do produto: [ymin, xmin, ymax, xmax], de 0 a 1000"),
    })
    .nullable()
    .describe("Onde está a FOTO do produto (sem textos, botões ou barras). Null se não houver foto clara"),
  avisos: z.array(z.string()).describe("O que ficou ilegível, cortado ou duvidoso"),
});
export type DadosPrint = z.infer<typeof DadosPrint>;

const SISTEMA_PRINT = `Você lê prints de anúncios da Shopee e do Mercado Livre e devolve os dados em JSON.
Regras que não podem ser quebradas:
- Copie apenas o que está ESCRITO na imagem. Nunca deduza, estime ou complete.
- Campo que não aparece com clareza fica null (ou lista vazia) e vira um aviso.
- Preço atual: o valor principal à vista. Não use preço de parcela, de frete nem de cupom.
- Preço anterior: só o valor que aparece riscado.
- Comissão: só a porcentagem de ganho do afiliado mostrada na tela (barra de afiliados, "Ganhe X%", "Ganhos X%").
- Valores em reais viram número com ponto decimal: "R$ 1.299,90" vira 1299.9.
- As imagens são do MESMO produto (topo do anúncio, descrição, características, avaliações). Junte tudo sem repetir.
- Características: copie as que estão escritas (cor, medidas, material, voltagem, selos, frete, loja oficial). Não liste meios de pagamento.
- Se alguma imagem parecer de outro produto, ignore-a e avise.
- Foto: marque o retângulo só da foto principal do produto, sem barra de status, botões, selos ou textos por cima.`;

export interface UsoIa {
  data: string;
  usados: number;
}

export class GeradorTexto {
  private client: Anthropic | null;
  private uso: UsoIa = { data: "", usados: 0 };

  private gemini: Gemini | null;

  constructor(
    private readonly opts: { apiKey?: string; modelo: string; limiteDiario: number; geminiKey?: string; geminiModelos?: string[] },
    client?: Anthropic,
    gemini?: Gemini,
  ) {
    this.client = client ?? (opts.apiKey ? new Anthropic({ apiKey: opts.apiKey, timeout: 60_000, maxRetries: 2 }) : null);
    this.gemini = gemini ?? (opts.geminiKey ? new Gemini({ apiKey: opts.geminiKey, modelos: opts.geminiModelos ?? ["gemini-flash-lite-latest", "gemini-flash-latest"] }) : null);
  }

  get configurada() {
    return this.client !== null || this.gemini !== null;
  }

  /** Claude quando tem chave da Anthropic; senão o Gemini grátis. */
  get provedor(): "claude" | "gemini" | null {
    return this.client ? "claude" : this.gemini ? "gemini" : null;
  }

  private exigirIa() {
    if (!this.configurada) throw new IntegracaoErro("nao_configurado", "IA não configurada (GEMINI_API_KEY ou ANTHROPIC_API_KEY)", 503);
  }

  usoHoje(): UsoIa {
    const hoje = new Date().toISOString().slice(0, 10);
    if (this.uso.data !== hoje) this.uso = { data: hoje, usados: 0 };
    return this.uso;
  }

  private async chamar(pedido: PedidoTexto, correcao?: string[]): Promise<TextosGerados> {
    const conteudo = JSON.stringify(pedido);
    const extra = correcao?.length ? `\n\nA versão anterior foi recusada por: ${correcao.join("; ")}. Corrija.` : "";
    if (!this.client) return this.gemini!.json(TextosGerados, SISTEMA, [{ text: `Dados do produto:\n${conteudo}${extra}` }]);
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
    this.exigirIa();
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
    return { textos, avisos: problemas, modelo: this.provedor === "claude" ? this.opts.modelo : "gemini", geradoEm: new Date().toISOString(), restantesHoje: this.opts.limiteDiario - uso.usados };
  }

  /** Lê prints do anúncio e devolve só o que está escrito neles. */
  async lerPrint(pedido: PedidoPrint) {
    this.exigirIa();
    const uso = this.usoHoje();
    if (uso.usados >= this.opts.limiteDiario) throw new IntegracaoErro("limite", `Limite diário de ${this.opts.limiteDiario} usos da IA atingido`, 429);
    uso.usados++;
    if (!this.client) {
      const dados = await this.gemini!.json(DadosPrint, SISTEMA_PRINT, [
        ...pedido.imagens.map((i) => ({ inlineData: { mimeType: i.tipo, data: i.base64 } })),
        { text: "Extraia os dados do produto destes prints." },
      ]);
      return { dados: limparPrint(dados, pedido.imagens.length), lidoEm: new Date().toISOString(), restantesHoje: this.opts.limiteDiario - uso.usados };
    }
    let resp;
    try {
      resp = await this.client.beta.messages.parse({
        model: this.opts.modelo,
        max_tokens: 4000,
        betas: ["server-side-fallback-2026-07-01"],
        fallbacks: "default",
        output_config: { effort: "low", format: betaZodOutputFormat(DadosPrint) },
        system: SISTEMA_PRINT,
        messages: [{
          role: "user",
          content: [
            ...pedido.imagens.map((i) => ({ type: "image" as const, source: { type: "base64" as const, media_type: i.tipo, data: i.base64 } })),
            { type: "text" as const, text: "Extraia os dados do produto destes prints." },
          ],
        }],
      });
    } catch (e) {
      if (e instanceof Anthropic.BadRequestError) throw new IntegracaoErro("resposta_invalida", "A IA não conseguiu abrir a imagem. Tente outro print.", 400);
      if (e instanceof Anthropic.AuthenticationError) throw new IntegracaoErro("autenticacao", "Chave da IA recusada", 401);
      if (e instanceof Anthropic.RateLimitError) throw new IntegracaoErro("limite", "IA ocupada (limite do provedor). Tente em instantes.", 429);
      if (e instanceof Anthropic.APIError) throw new IntegracaoErro("indisponivel", `Erro da IA (${e.status ?? "conexão"})`);
      throw e;
    }
    if (resp.stop_reason === "refusal") throw new IntegracaoErro("indisponivel", "A IA recusou ler este print. Preencha à mão.", 422);
    if (!resp.parsed_output) throw new IntegracaoErro("resposta_invalida", "A IA não devolveu o formato esperado", 502);
    return { dados: limparPrint(resp.parsed_output, pedido.imagens.length), lidoEm: new Date().toISOString(), restantesHoje: this.opts.limiteDiario - uso.usados };
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
      nome: "IA (textos e leitura de print)",
      estado: ok ? "configurado" : "nao_configurado",
      detalhe: !ok
        ? "Falta GEMINI_API_KEY (grátis) ou ANTHROPIC_API_KEY no backend"
        : `${this.provedor === "claude" ? `Claude (${this.opts.modelo})` : `Gemini grátis (${this.gemini!.modelos.join(", ")})`}. Usados hoje: ${uso.usados} de ${this.opts.limiteDiario}.`,
      verificadoEm: new Date().toISOString(),
      comoConfigurar: "Grátis: crie uma chave em aistudio.google.com (Get API key) e coloque em GEMINI_API_KEY no Render. Pago: ANTHROPIC_API_KEY (console.anthropic.com). Nunca no app.",
      operacoes: [
        { id: "gerar_texto", descricao: "Gerar textos promocionais", implementada: true, disponivel: ok, motivo: ok ? undefined : "Não configurado" },
        { id: "ler_print", descricao: "Preencher produto pelo print do anúncio", implementada: true, disponivel: ok, motivo: ok ? undefined : "Não configurado" },
      ],
    };
  }
}

/** Descarta valores impossíveis em vez de passar adiante um número errado. */
export function limparPrint(d: DadosPrint, nImagens = 1): DadosPrint {
  const avisos = [...d.avisos];
  const valor = (n: number | null, nome: string) => {
    if (n === null) return null;
    if (!Number.isFinite(n) || n <= 0 || n > 1_000_000) { avisos.push(`${nome} descartado: valor estranho (${n})`); return null; }
    return Math.round(n * 100) / 100;
  };
  const preco = valor(d.preco, "Preço");
  let precoAnterior = valor(d.precoAnterior, "Preço anterior");
  if (precoAnterior !== null && (preco === null || precoAnterior <= preco)) {
    avisos.push("Preço anterior descartado: não é maior que o preço atual");
    precoAnterior = null;
  }
  let comissao = d.comissaoPercentual;
  if (comissao !== null && (!Number.isFinite(comissao) || comissao <= 0 || comissao > 50)) {
    avisos.push(`Comissão descartada: ${comissao}% não parece real`);
    comissao = null;
  }
  let foto = d.foto ?? null;
  if (foto) {
    const [y0, x0, y1, x1] = foto.caixa;
    const ok = foto.caixa.length === 4 && foto.caixa.every((n) => Number.isFinite(n) && n >= 0 && n <= 1000)
      && foto.imagem >= 0 && foto.imagem < nImagens && y1! - y0! >= 100 && x1! - x0! >= 100;
    if (!ok) { avisos.push("Não deu para localizar a foto do produto"); foto = null; }
  }
  const avaliacao = d.avaliacao !== null && d.avaliacao >= 0 && d.avaliacao <= 5 ? d.avaliacao : null;
  return {
    ...d,
    nome: d.nome?.trim().slice(0, 200) || null,
    preco,
    precoAnterior,
    comissaoPercentual: comissao,
    avaliacao,
    caracteristicas: d.caracteristicas.map((c) => c.trim()).filter(Boolean).slice(0, 15).map((c) => c.slice(0, 200)),
    descricao: d.descricao?.slice(0, 2000) ?? null,
    foto,
    avisos: [...new Set(avisos)],
  };
}
