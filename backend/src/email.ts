import { IntegracaoErro } from "./integrations/types.js";
import { atualizacoesAte, expiraEm, PLANOS, type Licenca } from "./licencas.js";

/**
 * Manda a chave de acesso por e-mail (Brevo, API HTTP grátis: 300 e-mails por dia).
 * Render grátis bloqueia SMTP, então o envio é por HTTPS.
 */
export interface Email {
  configurado: boolean;
  enviarLicenca(l: Licenca): Promise<void>;
}

const esc = (s: string) => s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
const data = (ms: number) => new Date(ms).toLocaleDateString("pt-BR", { timeZone: "America/Sao_Paulo" });

export function textoLicenca(l: Licenca, site: string, suporte?: string) {
  const plano = PLANOS[l.plano].nome;
  const exp = expiraEm(l);
  const validade = exp ? `Válida até ${data(exp)} (renova a cada pagamento).` : "Vitalícia.";
  const atualiza = l.plano === "essencial" ? "Sem atualizações." : `Atualizações até ${data(atualizacoesAte(l))}.`;
  const assunto = `Sua chave do Achadinhos App (${plano})`;
  const html = `<div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;color:#1b1b1b">
<h2 style="margin:0 0 12px">Bem-vindo ao Achadinhos App 🎉</h2>
<p>Seu plano: <b>${plano}</b>. ${validade} ${atualiza}</p>
<p style="margin:18px 0 6px">Sua chave de acesso:</p>
<div style="font-family:monospace;font-size:22px;font-weight:bold;letter-spacing:1px;background:#f4efe3;border:1px solid #e0d3b0;border-radius:10px;padding:14px;text-align:center">${esc(l.codigo)}</div>
<p style="margin:22px 0"><a href="${esc(site)}/baixar" style="background:#c9ad74;color:#15120b;font-weight:bold;padding:14px 22px;border-radius:999px;text-decoration:none">Baixar o app</a></p>
<ol style="padding-left:18px;line-height:1.6">
<li>Toque em <b>Baixar o app</b> pelo celular Android.</li>
<li>Abra o arquivo e toque em <b>Instalar</b> (se pedir, permita instalar deste navegador).</li>
<li>Abra o app, toque em <b>Tenho um código</b> e cole a chave acima.</li>
<li>Siga a configuração guiada. Leva uns 15 minutos.</li>
</ol>
<p style="color:#666;font-size:13px">Não compartilhe a chave: ela vale para ${PLANOS[l.plano].aparelhos} celular(es).${suporte ? ` Dúvidas: <a href="${esc(suporte)}">fale com o suporte</a>.` : ""}</p>
</div>`;
  const texto = [
    `Obrigado pela compra! 🎉 Seu plano: *${plano}*`,
    ``,
    `🔑 Sua chave: *${l.codigo}*`,
    ``,
    `📲 Baixe o app: ${site}/baixar`,
    ``,
    `Como ativar:`,
    `1. Abra o link acima no celular Android e instale (se pedir, permita instalar).`,
    `2. Abra o app e toque em *Tenho um código*.`,
    `3. Cole a chave e siga a configuração guiada.`,
    ``,
    `Não compartilhe a chave: vale para ${PLANOS[l.plano].aparelhos} celular(es).`,
  ].join("\n");
  return { assunto, html, texto };
}

export class EmailBrevo implements Email {
  constructor(
    private readonly cfg: { chave?: string; remetente?: string; nome: string; site: string; suporte?: string },
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  get configurado() { return !!(this.cfg.chave && this.cfg.remetente); }

  async enviarLicenca(l: Licenca) {
    if (!this.configurado) throw new IntegracaoErro("nao_configurado", "E-mail não configurado (BREVO_API_KEY e EMAIL_REMETENTE)", 503);
    const m = textoLicenca(l, this.cfg.site, this.cfg.suporte);
    const r = await this.fetchImpl("https://api.brevo.com/v3/smtp/email", {
      method: "POST",
      headers: { "api-key": this.cfg.chave!, "content-type": "application/json", accept: "application/json" },
      body: JSON.stringify({
        sender: { email: this.cfg.remetente, name: this.cfg.nome },
        to: [{ email: l.email }],
        subject: m.assunto,
        htmlContent: m.html,
        textContent: m.texto,
      }),
      signal: AbortSignal.timeout(20_000),
    });
    if (!r.ok) {
      const corpo = await r.text().catch(() => "");
      throw new IntegracaoErro(r.status === 401 ? "autenticacao" : "indisponivel", `Brevo recusou o e-mail (${r.status}): ${corpo.slice(0, 200)}`, 502);
    }
  }
}
