# Venda do app

## Planos

| Plano | Preço sugerido | O que leva |
|---|---|---|
| Essencial | R$ 67, pagamento único | App da versão da compra, para sempre. 1 celular. Sem atualizações. |
| Pro (âncora) | R$ 147, pagamento único | 6 meses de atualizações e suporte. 2 celulares. |
| Premium (o destaque) | R$ 27,90 por mês | Sempre atualizado, suporte prioritário, 3 celulares. Cancela quando quiser. |

Preços ficam em `backend/src/paginas.ts` (`PRECOS`).

## Como funciona

1. O cliente compra na Kiwify ou na Hotmart.
2. A plataforma chama o webhook do servidor central. A licença é criada sozinha.
3. O cliente baixa o app em `/baixar` (link na página `/site`).
4. No app, digita o **e-mail da compra e o número do pedido** (vêm no e-mail da plataforma). Ou um código `ABOT-...` que você criou no `/admin`.
5. A configuração guiada abre sozinha: Telegram → Gemini → Supabase → Render → teste.
6. Reembolso ou chargeback desativam a licença. Assinatura sem renovar vence (3 dias de tolerância).

### Atualizações

- `scripts/publicar-versao.sh "Novidade 1" "Novidade 2"` gera o APK assinado e atualiza `backend/releases/versoes.json`. Depois: commit e push.
- O app avisa em Ajustes → Licença e atualizações.
- Quem não tem direito à versão nova (Essencial, Pro vencido, Premium cancelado) vê "Passar para o Premium".
- Se instalar uma versão mais nova que o plano, o app pede para baixar a versão do plano (`/baixar?ate=`).

### Servidor do cliente

- Cada cliente roda o próprio servidor grátis no Render, com a imagem pronta `ghcr.io/trinytsevenorg-del/afiliados-bot:latest`.
- A imagem é gerada sozinha pelo GitHub Actions (`.github/workflows/imagem-servidor.yml`) a cada push no `backend/`.
- Para atualizar o servidor, o cliente toca em **Manual Deploy → Deploy latest reference** no Render.

## O que o dono precisa fazer uma vez

1. **Render (seu servidor `achadinhos`) → Environment**, adicionar:
   - `LICENCA_CHAVE_PRIVADA` e `ADMIN_TOKEN` (estão no arquivo de segredos que o Claude entregou; nunca no Git).
   - `LINK_ESSENCIAL`, `LINK_PRO`, `LINK_PREMIUM` (links de checkout) e `LINK_SUPORTE` (ex.: `https://wa.me/55...`).
   - Kiwify: `KIWIFY_TOKEN`. Hotmart: `HOTMART_HOTTOK`.
   - `PRODUTOS_PLANOS` (opcional): `idDoProduto:essencial,idDoProduto:pro,idDoProduto:premium`. Sem isso, o plano sai do nome do produto (precisa conter "Essencial", "Pro" ou "Premium").
2. **Kiwify**: crie 3 produtos (Premium como assinatura mensal). Em Apps → Webhooks, URL `https://achadinhos-flqb.onrender.com/api/webhook/kiwify`, eventos de compra aprovada, reembolso, chargeback, assinatura renovada e cancelada. Copie o token para `KIWIFY_TOKEN`.
3. **Hotmart** (se usar): Ferramentas → Webhook, URL `https://achadinhos-flqb.onrender.com/api/webhook/hotmart`, versão 2.0. Copie o hottok para `HOTMART_HOTTOK`.
4. **GitHub → Packages → afiliados-bot → Package settings → Change visibility → Public** (uma vez, depois do primeiro build).
5. Faça uma compra de teste (cupom de 100% ou valor mínimo) e confira a licença em `/admin`.

## Chave de assinatura do APK

- `android/chave/` fica fora do Git. Guarde uma cópia em lugar seguro (Google Drive pessoal, por exemplo).
- Sem ela, os clientes não conseguem atualizar o app por cima: seria preciso desinstalar.
- Para gerar versões numa sessão nova, coloque a pasta de volta em `android/chave/`.

## Proteção contra cópia

- **APK embaralhado (R8):** nomes de classes trocados por letras, sem comentários nem logs. APK de 3 MB.
- **Licença assinada:** o app só abre com token assinado pela chave do dono (ECDSA). Não dá para inventar licença.
- **Licença presa ao celular:** limite por plano; trocar de celular só pelo `/admin`.
- **Clone reassinado não abre:** o app confere a impressão digital do próprio certificado.
- **Servidor do cliente exige app licenciado:** sem o token (`X-Licenca`), responde 402. Para usar a versão de desenvolvimento no seu servidor, coloque `LICENCA_LIVRE=1`.
- **Servidor embaralhado:** o JavaScript da imagem pública sai compactado (esbuild).
- **Limite honesto:** nenhum app Android é 100% à prova de cópia. Quem tiver muito conhecimento consegue quebrar; essas camadas tornam caro e demorado demais para valer a pena.

## Riscos

- WhatsApp automático não é oficial: está descrito na página e no app. Venda o Telegram como principal.
- Não prometer ganhos.
- O código do servidor está na imagem pública (JavaScript). A proteção é a licença no app.
