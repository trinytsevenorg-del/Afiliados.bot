// Gemini (Google AI Studio): plano gratuito, sem cartão. Usado quando não há chave da Anthropic.
// No plano gratuito o Google pode usar o conteúdo enviado para melhorar os modelos.
import { z } from "zod/v4";
import { fetchRetry } from "../lib/http.js";
import { IntegracaoErro } from "./types.js";

const BASE = "https://generativelanguage.googleapis.com/v1beta";

export type ParteGemini = { text: string } | { inlineData: { mimeType: string; data: string } };

export class Gemini {
  /** Modelo que bateu no limite do plano (429) fica de fora por 10 minutos. */
  private cheio = new Map<string, number>();

  constructor(
    private readonly opts: { apiKey: string; modelos: string[] },
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  get modelos() {
    return this.opts.modelos;
  }

  /** Pede um JSON no formato do schema e devolve só se passar na validação. */
  async json<T extends z.ZodType>(schema: T, sistema: string, partes: ParteGemini[], maxTokens = 4000): Promise<z.infer<T>> {
    const formato = "Responda SOMENTE com um objeto JSON neste formato (JSON Schema):\n" + JSON.stringify(z.toJSONSchema(schema));
    const corpo = JSON.stringify({
      systemInstruction: { parts: [{ text: `${sistema}\n\n${formato}` }] },
      contents: [{ role: "user", parts: partes }],
      generationConfig: { responseMimeType: "application/json", temperature: 0, maxOutputTokens: maxTokens },
    });
    const livres = this.opts.modelos.filter((m) => !((this.cheio.get(m) ?? 0) > Date.now()));
    const fila = livres.length ? livres : this.opts.modelos;
    let ultimo: IntegracaoErro = new IntegracaoErro("indisponivel", "Gemini indisponível");
    for (const modelo of fila) {
      let res: Response;
      try {
        res = await fetchRetry(`${BASE}/models/${encodeURIComponent(modelo)}:generateContent`, {
          method: "POST",
          headers: { "content-type": "application/json", "x-goog-api-key": this.opts.apiKey },
          body: corpo,
          timeoutMs: 45_000,
          // Sem repetir no mesmo modelo: limite ou pico (429/503) passa direto para o próximo.
          retries: 0,
          fetchImpl: this.fetchImpl,
        });
      } catch {
        ultimo = new IntegracaoErro("indisponivel", "O Gemini demorou demais para responder", 504);
        continue;
      }
      const body = (await res.json().catch(() => ({}))) as {
        candidates?: { content?: { parts?: { text?: string }[] }; finishReason?: string }[];
        error?: { status?: string; message?: string };
      };
      if (res.status === 429) {
        this.cheio.set(modelo, Date.now() + 10 * 60_000);
        ultimo = new IntegracaoErro("limite", "Limite do plano grátis do Gemini atingido. Tente mais tarde.", 429);
        continue;
      }
      if (res.status === 404 || res.status >= 500) {
        ultimo = new IntegracaoErro("indisponivel", `Gemini fora do ar (${res.status})`);
        continue;
      }
      if (res.status === 400 && /API_KEY|api key/i.test(body.error?.message ?? "")) throw new IntegracaoErro("autenticacao", "Chave do Gemini recusada", 401);
      if (res.status === 401 || res.status === 403) throw new IntegracaoErro("autenticacao", "Chave do Gemini recusada", 401);
      if (!res.ok) throw new IntegracaoErro("resposta_invalida", `Gemini recusou o pedido (${res.status})`, 400);
      const texto = (body.candidates?.[0]?.content?.parts ?? []).map((p) => p.text ?? "").join("").trim();
      if (!texto) throw new IntegracaoErro("indisponivel", "O Gemini não respondeu (conteúdo bloqueado ou vazio)", 422);
      let dados: unknown;
      try {
        dados = JSON.parse(texto.replace(/^```(?:json)?\s*/i, "").replace(/```\s*$/, ""));
      } catch {
        throw new IntegracaoErro("resposta_invalida", "O Gemini não devolveu um JSON válido", 502);
      }
      const r = schema.safeParse(dados);
      if (!r.success) throw new IntegracaoErro("resposta_invalida", "O Gemini devolveu dados fora do formato", 502);
      return r.data;
    }
    throw ultimo;
  }
}
