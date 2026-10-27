import { createPublicKey, verify, type KeyObject } from "node:crypto";

/**
 * O servidor do cliente só trabalha para app licenciado: o app manda o token (X-Licenca)
 * e aqui conferimos a assinatura com a chave PÚBLICA do dono (a mesma que vai no app).
 */
export const CHAVE_PUBLICA_PADRAO = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAESh+Hclbnt2Q4XYFTR0Gopmal4DygeKc8Ip+l9KXW0cwANrtQTR+il0KwdjLS6O9NAePo2IHVO+nhN2OrrtwElQ==";

export function chavePublica(base64Der = CHAVE_PUBLICA_PADRAO): KeyObject {
  return createPublicKey({ key: Buffer.from(base64Der, "base64"), format: "der", type: "spki" });
}

export type ResultadoLicenca = { ok: true; plano: string } | { ok: false; motivo: string };

export function conferidorDeLicenca(chave: KeyObject, agora: () => number = Date.now) {
  const cache = new Map<string, { plano: string; expira: number | null }>();
  return (token: string | undefined): ResultadoLicenca => {
    if (!token) return { ok: false, motivo: "App sem licença. Ative a licença no app." };
    let d = cache.get(token);
    if (!d) {
      const [dados, assinatura] = token.split(".");
      if (!dados || !assinatura) return { ok: false, motivo: "Licença inválida" };
      const bytes = Buffer.from(dados, "base64url");
      let valida = false;
      try { valida = verify("sha256", bytes, chave, Buffer.from(assinatura, "base64url")); } catch { valida = false; }
      if (!valida) return { ok: false, motivo: "Licença inválida" };
      const corpo = JSON.parse(bytes.toString()) as { p: string; e: number | null };
      d = { plano: corpo.p, expira: corpo.e ?? null };
      if (cache.size > 1000) cache.clear();
      cache.set(token, d);
    }
    if (d.expira !== null && agora() > d.expira) return { ok: false, motivo: "Licença vencida. Renove no app." };
    return { ok: true, plano: d.plano };
  };
}
