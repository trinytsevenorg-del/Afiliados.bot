import { EventEmitter } from "node:events";
import { describe, expect, it } from "vitest";
import { ArmazemMemoria, WhatsApp } from "../src/integrations/whatsapp.js";

/** Socket falso do Baileys: só o que o módulo usa. */
function socketFalso() {
  const ee = new EventEmitter();
  const enviados: { jid: string; conteudo: Record<string, unknown> }[] = [];
  const presencas: string[] = [];
  const sock = {
    ev: { on: (e: string, f: (...a: unknown[]) => void) => ee.on(e, f) },
    user: undefined as undefined | { id: string },
    requestPairingCode: async (tel: string) => (tel.startsWith("55") ? "ABCD1234" : ""),
    groupFetchAllParticipating: async () => ({
      "111@g.us": { id: "111@g.us", subject: "Achadinhos Cozinha", participants: [{ id: "5521999998888@s.whatsapp.net", admin: "admin" }, { id: "x@s.whatsapp.net" }], announce: true },
    }),
    groupMetadata: async (jid: string) => (jid === "111@g.us" ? { id: jid } : Promise.reject(new Error("não"))),
    sendPresenceUpdate: async (t: string) => { presencas.push(t); },
    sendMessage: async (jid: string, conteudo: Record<string, unknown>) => { enviados.push({ jid, conteudo }); return { key: { id: `m${enviados.length}` } }; },
    end: () => {},
    logout: async () => {},
  };
  return { sock, ee, enviados, presencas };
}

async function conectado(agora = () => 1_000_000_000) {
  const f = socketFalso();
  const armazem = new ArmazemMemoria();
  const wa = new WhatsApp(armazem, { intervaloMin: 10, maxDia: 2 }, (() => f.sock) as never, async () => {}, agora);
  const p = wa.conectar("(55) 21 99999-8888");
  await new Promise((r) => setImmediate(r));
  f.ee.emit("connection.update", { qr: "qr" });
  const r = await p;
  f.sock.user = { id: "5521999998888:3@s.whatsapp.net" };
  f.ee.emit("connection.update", { connection: "open" });
  return { wa, f, armazem, codigo: r.codigo };
}

describe("WhatsApp automático (número próprio)", () => {
  it("sem DATABASE_URL fica desligado e não tenta postar", async () => {
    const wa = new WhatsApp(null, { intervaloMin: 10, maxDia: 40 });
    expect(wa.status().estado).toBe("nao_configurado");
    await expect(wa.publicar({ chave: "fila-1-abcdef", grupo: "111@g.us", texto: "x" })).rejects.toMatchObject({ codigo: "nao_configurado" });
  });

  it("conecta pelo código de 8 letras e lista os grupos", async () => {
    const { wa, codigo } = await conectado();
    expect(codigo).toBe("ABCD-1234");
    expect(wa.resumo()).toMatchObject({ estado: "conectado", numero: "5521999998888" });
    expect(await wa.grupos()).toEqual([{ id: "111@g.us", nome: "Achadinhos Cozinha", participantes: 2, souAdmin: true, soAdminsEnviam: true }]);
  });

  it("recusa telefone fora do formato", async () => {
    const wa = new WhatsApp(new ArmazemMemoria(), { intervaloMin: 10, maxDia: 40 });
    await expect(wa.conectar("21999998888")).rejects.toMatchObject({ codigo: "resposta_invalida" });
  });

  it("posta com foto e legenda, digitando antes, e não repete a mesma chave", async () => {
    const { wa, f } = await conectado();
    const foto = { tipo: "image/jpeg", base64: Buffer.alloc(50, 1).toString("base64") };
    const r1 = await wa.publicar({ chave: "fila-1-abcdef", grupo: "111@g.us", texto: "💥 Oferta *R$ 21,00*", foto });
    const r2 = await wa.publicar({ chave: "fila-1-abcdef", grupo: "111@g.us", texto: "💥 Oferta *R$ 21,00*", foto });
    expect(r1).toMatchObject({ mensagemId: "m1", repetido: false });
    expect(r2).toMatchObject({ mensagemId: "m1", repetido: true });
    expect(f.enviados).toHaveLength(1);
    expect(f.enviados[0]!.conteudo.caption).toBe("💥 Oferta *R$ 21,00*");
    expect(f.presencas).toEqual(["composing", "paused"]);
  });

  it("respeita o intervalo mínimo e o limite diário por grupo", async () => {
    let t = 1_000_000_000;
    const { wa } = await conectado(() => t);
    await wa.publicar({ chave: "fila-1-abcdef", grupo: "111@g.us", texto: "a" });
    t += 5 * 60_000;
    await expect(wa.publicar({ chave: "fila-2-abcdef", grupo: "111@g.us", texto: "b" })).rejects.toMatchObject({ codigo: "limite" });
    t += 6 * 60_000;
    await wa.publicar({ chave: "fila-2-abcdef", grupo: "111@g.us", texto: "b" });
    t += 11 * 60_000;
    await expect(wa.publicar({ chave: "fila-3-abcdef", grupo: "111@g.us", texto: "c" })).rejects.toThrow(/Limite de 2/);
  });

  it("nunca manda no privado e recusa grupo em que não está", async () => {
    const { wa } = await conectado();
    await expect(wa.publicar({ chave: "fila-9-abcdef", grupo: "5521999990000@s.whatsapp.net", texto: "x" })).rejects.toMatchObject({ codigo: "resposta_invalida" });
    await expect(wa.publicar({ chave: "fila-8-abcdef", grupo: "222@g.us", texto: "x" })).rejects.toMatchObject({ codigo: "sem_acesso" });
  });

  it("se o WhatsApp desconectar o aparelho, apaga a sessão", async () => {
    const { wa, f, armazem } = await conectado();
    await armazem.gravar("creds", "{}");
    f.ee.emit("connection.update", { connection: "close", lastDisconnect: { error: { output: { statusCode: 401 } } } });
    await new Promise((r) => setImmediate(r));
    expect(wa.resumo().estado).toBe("desconectado");
    expect(await armazem.ler("creds")).toBeNull();
  });
});
