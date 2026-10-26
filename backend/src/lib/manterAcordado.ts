/**
 * Mantém o servidor acordado sem serviço de fora (cron-job.org).
 * O Render grátis dorme após 15 min sem visita: o próprio servidor se visita a cada 10 min.
 * O Render informa o endereço público em RENDER_EXTERNAL_URL.
 */
export function manterAcordado(url: string | undefined, fetchImpl: typeof fetch = fetch, intervaloMs = 10 * 60_000) {
  if (!url) return null;
  const alvo = `${url.replace(/\/+$/, "")}/api/health`;
  const t = setInterval(() => { void fetchImpl(alvo, { signal: AbortSignal.timeout(30_000) }).catch(() => undefined); }, intervaloMs);
  t.unref();
  return t;
}
