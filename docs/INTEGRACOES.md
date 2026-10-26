# Integrações: o que está pronto e o que falta

Atualizado em 26/10/2026. Cada integração mostra o estado real em **Ajustes → Integrações → Testar todas**.

## Shopee Afiliados (Open API)

- **Código pronto:** buscar produto (nome, preço, comissão, imagem, link de oferta, prazo) e gerar link de afiliado oficial (`generateShortLink`, com Sub ID).
- **Testado contra a API real:** a assinatura foi aceita. Com credenciais falsas, a Shopee respondeu `10035: você não tem acesso à Open API`. O backend mostra isso como "Aguardando autorização".
- **Falta (você):**
  1. Ter conta aprovada em affiliate.shopee.com.br.
  2. Pedir acesso à Open API no painel de afiliado.
  3. Colocar `SHOPEE_APP_ID` e `SHOPEE_SECRET` no `.env`.
- **Atenção:** os nomes dos campos (`productOfferV2`, `generateShortLink`) seguem a documentação conhecida da API. Quando o acesso sair, rode "Testar todas". Se algum campo mudou, o erro aparece claro e é só ajustar `backend/src/integrations/shopee.ts`.
- **Ainda não feito:** relatório de conversões (vendas). Fica para depois de ter acesso e ver o formato real.

## Mercado Livre

- **Código pronto:** consulta de produto (`/items/{id}`): nome, preço, preço anterior informado pelo ML, situação e imagem. O token se renova sozinho.
- **Testado:** sem token, a API do ML responde 403. Ou seja, precisa mesmo de app autorizado.
- **Não existe:** API oficial pública para gerar link de afiliado. O app **não** converte. Gere no painel de Afiliados do ML (Gerador de links ou Barra de Afiliados) e cole no app.
- **Falta (você):**
  1. Criar um app em developers.mercadolivre.com.br.
  2. Autorizar com a sua conta (fluxo `authorization_code`) e pegar o `refresh_token`.
  3. Colocar `ML_CLIENT_ID`, `ML_CLIENT_SECRET` e `ML_REFRESH_TOKEN` no `.env`. O novo refresh token fica salvo em `DATA_DIR/ml-token.json`.

## IA (Anthropic)

- **Código pronto:** `POST /api/copy/generate` com saída estruturada. A IA recebe só os dados salvos do produto.
- **Proteções:** o backend confere cada versão. Preço que não está nos dados, porcentagem sem desconto comprovado, "mais barato", falsa escassez, urgência sem fato, promessa de frete ou de ganho e link no texto fazem pedir uma correção à IA. Se o problema continuar, o texto volta com avisos em vermelho no app.
- **Limites:** `AI_DAILY_LIMIT` (padrão 60 por dia) e 10 por minuto.
- **Falta (você):** `ANTHROPIC_API_KEY` no `.env`. Modelo padrão: `claude-opus-5-5`, que pode ser trocado em `AI_MODEL`. Está ligado o fallback automático do servidor quando a IA recusa um pedido.

## Telegram (publicação automática)

- **Código pronto:** publicar em canal ou grupo onde o bot é administrador. Só marca "Publicado com confirmação" quando o Telegram devolve o ID da mensagem.
- **Sem duplicar:** cada item da fila tem uma chave. Se a conexão cair e o app tentar de novo, o backend devolve a publicação anterior e não posta outra vez, mesmo depois de reiniciar (fica em `DATA_DIR/telegram-publicacoes.json`).
- **Falta (você):** criar o bot no @BotFather, colocar `TELEGRAM_BOT_TOKEN` e adicionar o bot como admin do canal.

## WhatsApp

- **Funciona:** "Abrir no WhatsApp" pelo compartilhamento do Android. Depois o app pergunta **"Você publicou?"**. Abrir o WhatsApp nunca conta como enviado.
- **Não existe pela via oficial:** postar sozinho em grupos comuns. A API de Grupos da WhatsApp Business Platform exige conta oficial (OBA), só vale para grupos criados pela empresa e tem limite de participantes. Automação pelo WhatsApp Web não é usada, porque viola as regras e o número costuma ser banido.
- **Lembrete agendado:** no horário marcado, chega uma notificação "Hora de publicar". Você toca nela, revisa e envia.

## Variáveis do backend

Veja `backend/.env.example`. Nenhum valor real está no repositório.
