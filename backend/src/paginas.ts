/** Páginas simples servidas pelo servidor central: vendas (/site) e painel do dono (/admin). */

export const PRECOS = {
  essencial: { valor: "R$ 67", periodo: "pagamento único" },
  pro: { valor: "R$ 147", periodo: "pagamento único" },
  premium: { valor: "R$ 27,90", periodo: "por mês" },
};

const esc = (s: string) => s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);

const BASE_CSS = `
:root{--bg:#0b0b0d;--card:#141418;--linha:#26262c;--texto:#f2f0ea;--fraco:#a6a29a;--ouro:#c9ad74;--ouro2:#e6cf9c;--erro:#ff7a85}
*{box-sizing:border-box}html,body{margin:0;background:var(--bg);color:var(--texto);font-family:Figtree,system-ui,-apple-system,Segoe UI,Roboto,sans-serif;line-height:1.5}
a{color:var(--ouro2)}.wrap{max-width:1040px;margin:0 auto;padding:0 16px}
.btn{display:inline-block;background:var(--ouro);color:#15120b;font-weight:700;padding:14px 22px;border-radius:999px;text-decoration:none;border:0;cursor:pointer;font-size:16px}
.btn.sec{background:transparent;color:var(--ouro2);border:1px solid var(--linha)}
.card{background:var(--card);border:1px solid var(--linha);border-radius:20px;padding:22px}
`;

export function paginaSite(o: { essencial?: string; pro?: string; premium?: string; suporte?: string; versao?: string; porWhatsapp?: boolean }) {
  const link = (u?: string) => (u ? esc(u) : "#planos");
  const plano = (id: "essencial" | "pro" | "premium", nome: string, itens: string[], destaque = false) => `
    <div class="card plano${destaque ? " destaque" : ""}">
      ${destaque ? '<div class="selo">Mais vantajoso</div>' : ""}
      <h3>${nome}</h3>
      <div class="preco">${PRECOS[id].valor}<span>${PRECOS[id].periodo}</span></div>
      <ul>${itens.map((i) => `<li>${i}</li>`).join("")}</ul>
      <a class="btn${destaque ? "" : " sec"}" href="${link(o[id])}">${o.porWhatsapp ? `Comprar o ${nome} pelo WhatsApp` : `Quero o ${nome}`}</a>
    </div>`;
  return `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Afiliados.bot · Robô de achadinhos</title>
<meta name="description" content="App que posta suas ofertas de afiliado sozinho no Telegram e no WhatsApp.">
<link rel="preconnect" href="https://fonts.googleapis.com"><link href="https://fonts.googleapis.com/css2?family=Figtree:wght@400;600;800&display=swap" rel="stylesheet">
<style>${BASE_CSS}
header{padding:56px 0 32px}.tag{color:var(--ouro2);font-weight:600;letter-spacing:.04em;text-transform:uppercase;font-size:13px}
h1{font-size:clamp(32px,6vw,54px);line-height:1.08;margin:12px 0 16px;font-weight:800}h1 em{color:var(--ouro2);font-style:normal}
.sub{color:var(--fraco);font-size:18px;max-width:640px}.acoes{display:flex;gap:12px;flex-wrap:wrap;margin-top:24px}
section{padding:36px 0}h2{font-size:28px;margin:0 0 18px}
.grade{display:grid;gap:14px;grid-template-columns:repeat(auto-fit,minmax(230px,1fr))}
.passo b{display:block;color:var(--ouro2);font-size:14px;margin-bottom:6px}.passo p{margin:0;color:var(--fraco)}
.plano{display:flex;flex-direction:column;gap:10px;position:relative}.plano h3{margin:0;font-size:22px}
.plano.destaque{border-color:var(--ouro);box-shadow:0 0 0 1px var(--ouro) inset}
.selo{position:absolute;top:-12px;right:16px;background:var(--ouro);color:#15120b;font-weight:700;font-size:12px;padding:4px 10px;border-radius:999px}
.preco{font-size:34px;font-weight:800}.preco span{display:block;font-size:14px;color:var(--fraco);font-weight:400}
.plano ul{margin:0 0 8px;padding-left:18px;color:var(--fraco)}.plano li{margin:4px 0}.plano .btn{text-align:center;margin-top:auto}
details{border-bottom:1px solid var(--linha);padding:14px 0}summary{cursor:pointer;font-weight:600}details p{color:var(--fraco);margin:8px 0 0}
.aviso{color:var(--fraco);font-size:13px}footer{padding:32px 0 48px;color:var(--fraco);font-size:13px}
</style></head><body>
<header class="wrap">
  <div class="tag">Afiliados.bot</div>
  <h1>Suas ofertas de afiliado <em>postando sozinhas</em> no Telegram e no WhatsApp</h1>
  <p class="sub">Mande o print do produto. A IA lê o preço, escreve a oferta e o robô posta nos seus grupos, um de cada vez, no horário certo. Mesmo com o celular desligado.</p>
  <div class="acoes"><a class="btn" href="#planos">Ver planos</a>${o.suporte ? `<a class="btn sec" href="${esc(o.suporte)}">Tirar dúvida</a>` : ""}</div>
</header>
<section class="wrap"><h2>Como funciona</h2><div class="grade">
  <div class="card passo"><b>1. Print</b><p>Tire print do produto na Shopee ou no Mercado Livre. Pode mandar até 30 de uma vez.</p></div>
  <div class="card passo"><b>2. IA preenche</b><p>Nome, preço, preço antigo, cupom, foto e nicho saem do print. Você só cola o seu link.</p></div>
  <div class="card passo"><b>3. Robô posta</b><p>Cada nicho vai para o seu grupo ou canal, uma oferta a cada 15 ou 30 minutos.</p></div>
  <div class="card passo"><b>4. Você acompanha</b><p>Veja o que saiu, o que falhou e as comissões, tudo no app.</p></div>
</div></section>
<section class="wrap"><h2>O que vem no app</h2><div class="grade">
  <div class="card passo"><b>Leitura de print com IA</b><p>Até 10 fotos por produto, recortadas do print.</p></div>
  <div class="card passo"><b>Telegram automático</b><p>Um bot para todos os seus canais, um canal por nicho.</p></div>
  <div class="card passo"><b>WhatsApp automático</b><p>Conecta um número seu e posta nos seus grupos com intervalo e limite.</p></div>
  <div class="card passo"><b>Fila no servidor</b><p>Agende o dia todo e desligue o celular.</p></div>
  <div class="card passo"><b>Modelo de mensagem</b><p>De / Por / cupom / link, no padrão dos grandes grupos de promoção.</p></div>
  <div class="card passo"><b>Passo a passo no app</b><p>Configuração guiada com imagens. Sem programar, sem computador.</p></div>
</div></section>
<section class="wrap" id="planos"><h2>Planos</h2><div class="grade">
  ${plano("essencial", "Essencial", ["App completo, versão atual", "Pagamento único, seu para sempre", "A gente te ensina a configurar", "1 celular", "Sem atualizações"])}
  ${plano("premium", "Premium", ["Tudo do app, sempre atualizado", "A gente configura pra você", "Novidades chegam sozinhas no app", "Suporte prioritário", "Até 3 celulares", "Cancele quando quiser"], true)}
  ${plano("pro", "Pro", ["App completo", "Pagamento único", "A gente configura pra você", "6 meses de atualizações", "Suporte por 6 meses", "Até 2 celulares"])}
</div>
<p class="aviso">${o.porWhatsapp
    ? "O botão abre o nosso WhatsApp. Depois do pagamento, você recebe ali mesmo a chave de acesso e o link para baixar o app."
    : "Depois da compra, você recebe no e-mail a chave de acesso (ou use o e-mail e o número do pedido) e o link para baixar o app."}</p></section>
<section class="wrap"><h2>Perguntas</h2>
  <details><summary>Preciso de computador?</summary><p>Não. Tudo é feito pelo celular, com o passo a passo do app.</p></details>
  <details><summary>Tem custo além do plano?</summary><p>Não é obrigatório. O app usa contas gratuitas (Google Gemini, Supabase e Render) que você cria com os botões do passo a passo.</p></details>
  <details><summary>Funciona com Shopee e Mercado Livre?</summary><p>Sim. Você usa o seu próprio link de afiliado; o app não altera o link.</p></details>
  <details><summary>O WhatsApp automático é oficial?</summary><p>Não. Ele conecta como o WhatsApp Web, e o WhatsApp pode banir números que abusam. Use um chip só para isso e respeite o intervalo. O Telegram é oficial e sem esse risco.</p></details>
  <details><summary>Quanto vou ganhar?</summary><p>Depende dos seus grupos, nichos e produtos. O app automatiza a divulgação; não prometemos ganhos.</p></details>
  <details><summary>Como baixo o app?</summary><p><a href="/baixar">Baixe aqui</a>${o.versao ? ` (versão ${esc(o.versao)})` : ""}. No Android, permita instalar de fontes desconhecidas quando ele pedir.</p></details>
</section>
<footer class="wrap">Afiliados.bot · Barbosa Store${o.suporte ? ` · <a href="${esc(o.suporte)}">Suporte</a>` : ""}</footer>
</body></html>`;
}

export function paginaAdmin() {
  return `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Painel de licenças</title><style>${BASE_CSS}
body{padding:24px 0}h1{font-size:24px}label{display:block;font-size:13px;color:var(--fraco);margin:10px 0 4px}
input,select{width:100%;background:#0f0f12;color:var(--texto);border:1px solid var(--linha);border-radius:12px;padding:12px;font-size:16px}
.linha{display:grid;gap:10px;grid-template-columns:repeat(auto-fit,minmax(180px,1fr))}.card{margin:14px 0}
table{width:100%;border-collapse:collapse;font-size:14px}td,th{border-bottom:1px solid var(--linha);padding:8px 6px;text-align:left;vertical-align:top}
.tab{overflow-x:auto}.codigo{font-family:ui-monospace,monospace;color:var(--ouro2)}.msg{margin-top:10px}.erro{color:var(--erro)}
td button{background:transparent;color:var(--ouro2);border:1px solid var(--linha);border-radius:999px;padding:6px 10px;cursor:pointer;margin:2px}
</style></head><body><div class="wrap">
<h1>Painel de licenças</h1>
<div class="card"><label>Token do painel (ADMIN_TOKEN)</label><input id="tok" type="password" autocomplete="off"><div class="msg" id="m0"></div></div>
<div class="card"><b>Criar licença (venda por Pix ou presente)</b>
  <div class="linha"><div><label>Plano</label><select id="plano"><option value="essencial">Essencial</option><option value="pro">Pro</option><option value="premium">Premium</option></select></div>
  <div><label>WhatsApp do cliente (com DDD)</label><input id="zapcli" type="tel" placeholder="67999999999"></div><div><label>Meses (Premium)</label><input id="meses" type="number" value="1" min="1" max="36"></div></div>
  <p><button class="btn" id="criar">Criar chave e abrir o WhatsApp</button></p><div class="msg" id="m1"></div></div>
<div class="card"><b>Licenças</b> <button class="btn sec" id="atualizar" style="padding:6px 14px;font-size:14px">Atualizar</button>
  <div class="tab"><table><thead><tr><th>Código</th><th>Plano</th><th>E-mail / pedido</th><th>Situação</th><th>Celulares</th><th></th></tr></thead><tbody id="lista"></tbody></table></div></div>
</div><script>
const $=id=>document.getElementById(id);
try{$("tok").value=localStorage.getItem("adm")||""}catch(e){}
$("tok").onchange=()=>{try{localStorage.setItem("adm",$("tok").value)}catch(e){};carregar()};
const data=ms=>ms?new Date(ms).toLocaleDateString("pt-BR"):"sempre";
async function api(m,u,b){const r=await fetch(u,{method:m,headers:{authorization:"Bearer "+$("tok").value,"content-type":"application/json"},body:b?JSON.stringify(b):undefined});const j=await r.json().catch(()=>({}));if(!r.ok)throw new Error(j.mensagem||("Erro "+r.status));return j}
function esc(s){return String(s??"").replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"})[c])}
async function carregar(){try{const j=await api("GET","/api/admin/licencas");$("m0").textContent=j.licencas.length+" licenças";$("m0").className="msg";
$("lista").innerHTML=j.licencas.map(l=>'<tr><td class="codigo">'+esc(l.codigo)+'</td><td>'+esc(l.nomePlano)+'</td><td>'+esc(l.email)+'<br><small>'+esc(l.origem)+' · '+esc(l.pedido)+'</small></td><td>'+(l.ativo?'ativa':'<span class="erro">desativada</span>')+'<br><small>expira: '+data(l.expira)+' · atualiza até '+data(l.atualizacoesAte)+'</small>'+(l.motivo?'<br><small>'+esc(l.motivo)+'</small>':'')+'</td><td>'+l.aparelhos.length+'</td><td><button data-a="reenviar" data-c="'+esc(l.codigo)+'">Mandar de novo</button><button data-a="liberar" data-c="'+esc(l.codigo)+'">Liberar celulares</button>'+(l.ativo?'<button data-a="desativar" data-c="'+esc(l.codigo)+'">Desativar</button>':'')+'</td></tr>').join("")}catch(e){$("m0").textContent=e.message;$("m0").className="msg erro"}}
let zap="";
function numeroDe(l){const m=/^whatsapp:(\d+)$/.exec(l.email||"");if(!m)return"";const n=m[1];return n.startsWith("55")?n:"55"+n}
function resultado(l,abrir){zap=l.whatsapp||"";const n=numeroDe(l);const url="https://wa.me/"+n+"?text="+encodeURIComponent(zap);
$("m1").className="msg";$("m1").innerHTML='Chave: <span class="codigo">'+esc(l.codigo)+'</span>'+(l.emailEnviado?'<br>✅ Também foi por e-mail':'')+'<br><a class="btn" id="abrirzap" style="margin-top:8px;padding:10px 16px;font-size:15px" target="_blank" href="'+esc(url)+'">Mandar no WhatsApp'+(n?'':' (escolher contato)')+'</a> <button class="btn sec" id="zap" style="margin-top:8px;padding:8px 14px;font-size:14px">Copiar mensagem</button>';
$("zap").onclick=async()=>{try{await navigator.clipboard.writeText(zap);$("zap").textContent="Copiado ✅"}catch(e){prompt("Copie:",zap)}};if(abrir)window.open(url,"_blank")}
$("lista").onclick=async e=>{const b=e.target.closest("button");if(!b)return;if(b.dataset.a==="desativar"&&!confirm("Desativar esta licença?"))return;try{const r=await api("POST","/api/admin/licencas/"+b.dataset.c+"/"+b.dataset.a);if(b.dataset.a==="reenviar"){resultado(r,false);scrollTo(0,0)}carregar()}catch(x){alert(x.message)}};
$("criar").onclick=async()=>{try{const l=await api("POST","/api/admin/licencas",{plano:$("plano").value,whatsapp:$("zapcli").value,meses:Number($("meses").value)||1});
resultado(l,true);carregar()}catch(e){$("m1").textContent=e.message;$("m1").className="msg erro"}};
$("atualizar").onclick=carregar;if($("tok").value)carregar();
</script></body></html>`;
}
