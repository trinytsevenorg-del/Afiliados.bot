import pg from "pg";
import { describe, expect, it } from "vitest";
import { ArmazemFilaPostgres, type ItemServidor } from "../src/fila.js";
import { ArmazemPostgres } from "../src/integrations/whatsapp.js";

// Roda só quando há um Postgres de teste (PG_TESTE=postgres://...).
const url = process.env.PG_TESTE;

describe.skipIf(!url)("Armazém no Postgres", () => {
  it("guarda sessão e envios, e apaga a sessão", async () => {
    const pool = new pg.Pool({ connectionString: url });
    await pool.query("DROP TABLE IF EXISTS wa_sessao, wa_envios");
    const a = new ArmazemPostgres(pool);
    await a.gravar("creds", '{"x":1}');
    await a.gravar("creds", '{"x":2}');
    expect(await a.ler("creds")).toBe('{"x":2}');
    await a.registrarEnvio("fila-1", "111@g.us", "m1", 1000);
    await a.registrarEnvio("fila-1", "111@g.us", "m9", 2000);
    expect(await a.envio("fila-1")).toEqual({ grupo: "111@g.us", mensagemId: "m1", quando: 1000 });
    await a.registrarEnvio("fila-2", "111@g.us", "m2", 5000);
    expect(await a.enviosDoGrupo("111@g.us", 1500)).toEqual([5000]);
    await a.apagarSessao();
    expect(await a.ler("creds")).toBeNull();
    expect(await a.envio("fila-2")).not.toBeNull();
    await pool.end();
  });

  it("fila: grava, acha os vencidos e os presos, e limpa os velhos", async () => {
    const pool = new pg.Pool({ connectionString: url });
    await pool.query("DROP TABLE IF EXISTS fila_servidor");
    const a = new ArmazemFilaPostgres(pool);
    const base: ItemServidor = { chave: "fila-1", canal: "telegram", destino: "@x", texto: "oi", quando: 1000, estado: "agendado", resultado: null, mensagemId: null, tentativas: 0, criadoEm: 1, atualizadoEm: 1 };
    await a.salvar(base);
    await a.salvar({ ...base, chave: "fila-2", quando: 5000 });
    await a.salvar({ ...base, chave: "fila-3", estado: "enviando", atualizadoEm: 10 });
    expect((await a.vencidos(2000, 5)).map((i) => i.chave)).toEqual(["fila-1"]);
    expect((await a.presos(100)).map((i) => i.chave)).toEqual(["fila-3"]);
    await a.salvar({ ...base, estado: "publicado", mensagemId: "77", atualizadoEm: 50 });
    expect(await a.pegar("fila-1")).toMatchObject({ estado: "publicado", mensagemId: "77", quando: 1000 });
    await a.limpar(100);
    expect(await a.pegar("fila-1")).toBeNull();
    expect(await a.pegar("fila-2")).not.toBeNull();
    await pool.end();
  });
});
