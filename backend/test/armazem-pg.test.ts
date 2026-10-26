import pg from "pg";
import { describe, expect, it } from "vitest";
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
});
