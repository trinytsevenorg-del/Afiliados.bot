# Primeiros passos: do zero, pelo celular

Tempo: uns 30 minutos. Custo: R$ 0 para começar.

## O que é o "backend", em uma frase

É um pequeno programa que fica ligado na internet e guarda as suas senhas (IA, Telegram, Shopee).
O app no celular conversa com ele. O **endereço do backend** é o link desse programa, por exemplo `https://afiliados-bot.onrender.com`.
Você ainda não tem esse endereço: ele nasce no Passo 2.

## Passo 1 · Criar o bot do Telegram (5 min)

1. Abra o Telegram e procure **@BotFather** (tem o selo azul).
2. Toque em **Iniciar** e mande `/newbot`.
3. Ele pede um **nome**: mande `Ofertas do Fulano` (qualquer um).
4. Ele pede um **usuário**, que precisa terminar em `bot`: mande algo como `ofertasdofulano_bot`.
5. Ele responde com um **token**, parecido com `7123456789:AAH...`. **Copie e guarde.** É uma senha: não mostre a ninguém.
6. Crie um **canal**: Telegram → lápis → Novo canal → Público → escolha o link, ex.: `t.me/ofertasfulano`.
7. No canal: Administradores → Adicionar administrador → procure o seu bot → permita **Publicar mensagens**.

Guarde também o nome do canal com @ na frente: `@ofertasfulano`.

## Passo 2 · Colocar o backend no ar no Render (10 min)

1. No navegador do celular, abra **render.com** e toque em **Get Started**.
2. Escolha **GitHub** e entre com a conta `trinytsevenorg-del`.
3. O Render pede acesso ao GitHub. Permita o repositório **Afiliados.bot**.
4. No painel do Render: **New +** → **Blueprint**.
5. Escolha o repositório **Afiliados.bot**. O Render lê o arquivo `render.yaml` sozinho.
6. Ele mostra os campos para preencher:
   - `TELEGRAM_BOT_TOKEN`: cole o token do Passo 1.
   - `ANTHROPIC_API_KEY`: deixe em branco por enquanto (Passo 4).
   - `SHOPEE_APP_ID` e `SHOPEE_SECRET`: deixe em branco (só depois que a Shopee aprovar).
7. Toque em **Apply** (ou Deploy). Espere uns 5 minutos até aparecer **Live** em verde.
8. No topo da página do serviço aparece o **endereço**, tipo `https://afiliados-bot-xxxx.onrender.com`. **Esse é o endereço do backend.**
9. Teste: abra no navegador o endereço + `/api/health`. Se aparecer `{"ok":true...}`, está funcionando.

### Pegar o token do app

1. Ainda no Render, abra o serviço → **Environment**.
2. Ache **APP_TOKEN** (o Render criou sozinho) → toque no olho → copie.

## Passo 3 · Ligar o app ao backend (2 min)

1. Abra o Afiliados.bot → **Ajustes**.
2. **Endereço:** cole o endereço do Passo 2 (sem `/api/health` no fim).
3. **Token do app:** cole o `APP_TOKEN`.
4. **Salvar** → **Testar conexão**. Deve aparecer "Backend respondeu".
5. Em **Integrações** → **Testar todas**: o Telegram deve aparecer como **Configurado**.
6. Em **Canais de divulgação** → **Adicionar**:
   - Tipo: **Telegram (automático)**. Destino: `@ofertasfulano`. Toque em **Testar destino**.
   - Adicione também um canal do tipo **WhatsApp (compartilhar)** para os seus grupos.

Pronto: o app já cadastra produtos, publica sozinho no Telegram e abre o WhatsApp para você enviar.

## Passo 4 · IA para escrever os textos (opcional, 5 min)

1. Abra **console.anthropic.com** e crie a conta.
2. Em **Billing**, coloque um crédito pequeno (ex.: US$ 5). Sem crédito, a IA não responde.
3. Em **API Keys** → **Create Key** → copie (começa com `sk-ant-`).
4. No Render: serviço → **Environment** → `ANTHROPIC_API_KEY` → cole → **Save**. O Render reinicia sozinho.
5. No app: **Ajustes → Testar todas**: a IA deve aparecer como **Configurado**.

Sem a IA, você escreve os textos à mão no próprio app.

## Passo 5 · Shopee e Mercado Livre (quando der)

- **Shopee:** entre em affiliate.shopee.com.br. Peça acesso à **Open API**. Quando aprovarem, cole o App ID e o Secret no Render (Environment).
- **Mercado Livre:** o link de afiliado é gerado no painel de Afiliados do ML e colado no app. A consulta automática de preço do ML é opcional e fica para depois.

## Coisas boas de saber

- **Plano grátis do Render dorme** depois de 15 minutos parado. O primeiro uso depois disso demora **até 1 minuto**. Depois fica rápido.
- No plano grátis, o "histórico anti-repetição" do Telegram some quando o Render reinicia. O app continua protegendo pelo estado da fila. Se quiser 100%, o plano pago (US$ 7/mês) com disco resolve.
- **Nunca** mande tokens por print ou grupo. Se vazar, gere outro (BotFather: `/revoke`; Anthropic: apague a chave; Render: troque o APP_TOKEN e cole de novo no app).
