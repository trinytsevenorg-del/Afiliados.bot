import { describe, expect, it } from "vitest";
import { ArmazemFilaMemoria, FilaServidor } from "../src/fila.js";
import { IntegracaoErro } from "../src/integrations/types.js";

const MIN = 60_000;

function montar(opcoes: { telegramFalha?: Error; whatsappFalha?: Error } = {}) {
  let agora = 1_000_000;
  const envios: { canal: string; destino: string; texto: string; fotos: number }[] = [];
  const armazem = new ArmazemFilaMemoria();
  const fila = new FilaServidor(armazem, {
    telegram: {
      publicar: async (p: { chatId: string; texto: string; fotos?: unknown[] | null }) => {
        if (opcoes.telegramFalha) throw opcoes.telegramFalha;
        envios.push({ canal: "telegram", destino: p.chatId, texto: p.texto, fotos: p.fotos?.length ?? 0 });
        return { chatId: p.chatId, messageId: 77, publicadoEm: "x", repetido: false };
      },
    } as never,
    whatsapp: {
      publicar: async (p: { grupo: string; texto: string; foto?: unknown }) => {
        if (opcoes.whatsappFalha) throw opcoes.whatsappFalha;
        envios.push({ canal: "whatsapp", destino: p.grupo, texto: p.texto, fotos: p.foto ? 1 : 0 });
        return { grupo: p.grupo, mensagemId: "w1", quando: agora };
      },
    } as never,
  }, () => agora);
  return { fila, armazem, envios, passar: (ms: number) => { agora += ms; }, agora: () => agora };
}

const foto = { tipo: "image/jpeg" as const, base64: "A".repeat(200) };

describe("fila no servidor", () => {
  it("posta só na hora marcada e guarda a confirmação", async () => {
    const t = montar();
    await t.fila.agendar({ chave: "fila-aaaaaaaa", canal: "telegram", destino: "@meucanal", texto: "oi", fotos: [foto], quando: t.agora() + 15 * MIN });
    expect(await t.fila.rodar()).toBe(0);
    t.passar(15 * MIN);
    expect(await t.fila.rodar()).toBe(1);
    expect(t.envios).toEqual([{ canal: "telegram", destino: "@meucanal", texto: "oi", fotos: 1 }]);
    const [s] = await t.fila.consultar(["fila-aaaaaaaa"]);
    expect(s).toMatchObject({ estado: "publicado", mensagemId: "77" });
    // Depois de publicado, as fotos saem do banco e não reenvia.
    expect((await t.armazem.pegar("fila-aaaaaaaa"))!.fotos).toBeNull();
    await t.fila.agendar({ chave: "fila-aaaaaaaa", canal: "telegram", destino: "@meucanal", texto: "oi", quando: t.agora() });
    expect(await t.fila.rodar()).toBe(0);
  });

  it("WhatsApp manda a primeira foto; limite do WhatsApp só adia", async () => {
    const t = montar({ whatsappFalha: new IntegracaoErro("limite", "Espere 10 min neste grupo", 429) });
    await t.fila.agendar({ chave: "fila-bbbbbbbb", canal: "whatsapp", destino: "123-456@g.us", texto: "oi", fotos: [foto, foto], quando: t.agora() });
    await t.fila.rodar();
    const [s] = await t.fila.consultar(["fila-bbbbbbbb"]);
    expect(s).toMatchObject({ estado: "agendado", quando: t.agora() + 10 * MIN });
  });

  it("erro passageiro tenta de novo; erro definitivo vira falha", async () => {
    const t = montar({ telegramFalha: new IntegracaoErro("indisponivel", "Telegram fora", 502) });
    await t.fila.agendar({ chave: "fila-cccccccc", canal: "telegram", destino: "@meucanal", texto: "oi", quando: t.agora() });
    for (let i = 0; i < 6; i++) { await t.fila.rodar(); t.passar(5 * MIN); }
    expect((await t.fila.consultar(["fila-cccccccc"]))[0]).toMatchObject({ estado: "falha", resultado: "Telegram fora" });

    const t2 = montar({ telegramFalha: new IntegracaoErro("sem_acesso", "Bot não é admin", 403) });
    await t2.fila.agendar({ chave: "fila-dddddddd", canal: "telegram", destino: "@meucanal", texto: "oi", quando: t2.agora() });
    await t2.fila.rodar();
    expect((await t2.fila.consultar(["fila-dddddddd"]))[0]).toMatchObject({ estado: "falha", resultado: "Bot não é admin" });
  });

  it("preço vencido não sai", async () => {
    const t = montar();
    await t.fila.agendar({ chave: "fila-eeeeeeee", canal: "telegram", destino: "@meucanal", texto: "oi", quando: t.agora() + 60 * MIN, validoAte: t.agora() + 30 * MIN });
    t.passar(60 * MIN);
    await t.fila.rodar();
    expect(t.envios).toEqual([]);
    expect((await t.fila.consultar(["fila-eeeeeeee"]))[0]!.estado).toBe("falha");
  });

  it("cancela antes de sair; preso em 'enviando' vira falha sem reenviar", async () => {
    const t = montar();
    await t.fila.agendar({ chave: "fila-ffffffff", canal: "telegram", destino: "@meucanal", texto: "oi", quando: t.agora() + MIN });
    expect((await t.fila.cancelar("fila-ffffffff"))!.estado).toBe("cancelado");
    t.passar(MIN);
    await t.fila.rodar();
    expect(t.envios).toEqual([]);

    const i = (await t.armazem.pegar("fila-ffffffff"))!;
    await t.armazem.salvar({ ...i, chave: "fila-gggggggg", estado: "enviando", atualizadoEm: t.agora() });
    t.passar(6 * MIN);
    await t.fila.rodar();
    expect((await t.fila.consultar(["fila-gggggggg"]))[0]!.estado).toBe("falha");
    expect(t.envios).toEqual([]);
  });

  it("sem banco a fila fica desligada", async () => {
    const f = new FilaServidor(null, { telegram: {} as never, whatsapp: {} as never });
    expect(f.ativa).toBe(false);
    await expect(f.consultar(["fila-hhhhhhhh"])).rejects.toMatchObject({ httpStatus: 503 });
  });
});
