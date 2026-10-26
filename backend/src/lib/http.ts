// Requisições com timeout, repetição limitada e intervalo progressivo.

export class HttpError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly body?: string,
  ) {
    super(message);
  }
}

export interface FetchOptions extends RequestInit {
  timeoutMs?: number;
  retries?: number;
  /** Para testes: substitui o fetch global. */
  fetchImpl?: typeof fetch;
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

function retryable(status: number) {
  return status === 429 || status >= 500;
}

export async function fetchRetry(url: string, opts: FetchOptions = {}): Promise<Response> {
  const { timeoutMs = 10_000, retries = 2, fetchImpl = fetch, ...init } = opts;
  let lastErr: unknown;
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const res = await fetchImpl(url, { ...init, signal: AbortSignal.timeout(timeoutMs) });
      if (!retryable(res.status) || attempt === retries) return res;
      const ra = Number(res.headers.get("retry-after"));
      await sleep(Number.isFinite(ra) && ra > 0 ? Math.min(ra, 10) * 1000 : 400 * 2 ** attempt);
    } catch (err) {
      lastErr = err;
      if (attempt === retries) break;
      await sleep(400 * 2 ** attempt);
    }
  }
  const e = lastErr as Error | undefined;
  const timeout = e?.name === "TimeoutError" || e?.name === "AbortError";
  throw new HttpError(timeout ? 504 : 502, timeout ? "Tempo esgotado ao chamar o serviço" : "Falha de conexão com o serviço");
}
