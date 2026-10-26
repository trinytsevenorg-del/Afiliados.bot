import { afterEach, describe, expect, it, vi } from "vitest";
import { manterAcordado } from "../src/lib/manterAcordado.js";

describe("manter acordado", () => {
  afterEach(() => vi.useRealTimers());

  it("visita /api/health a cada 10 minutos", async () => {
    vi.useFakeTimers();
    const chamadas: string[] = [];
    const t = manterAcordado("https://x.onrender.com/", (async (u: string) => { chamadas.push(u); return new Response("{}"); }) as typeof fetch);
    await vi.advanceTimersByTimeAsync(25 * 60_000);
    clearInterval(t!);
    expect(chamadas).toEqual(["https://x.onrender.com/api/health", "https://x.onrender.com/api/health"]);
  });

  it("sem endereço não faz nada", () => {
    expect(manterAcordado(undefined)).toBeNull();
  });
});
