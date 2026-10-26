# Plano para vender o app (futuro)

Ideia do dono. Ainda não implementado. Ordem sugerida no fim.

## Como o cliente compra e entra

1. Site de vendas: a pessoa paga (ex.: Mercado Pago).
2. O pagamento confirmado gera um **código de acesso** e manda por e-mail.
3. A pessoa baixa o app e digita o código.
4. O código libera a criação da conta dela no app.

## Cada cliente usa as próprias contas grátis

O app guia a pessoa, sem precisar de outras IAs nem de ajuda:

- Tela inicial com um botão para cada serviço:
  - **Criar conta no Gemini** (aistudio.google.com) → copiar a chave.
  - **Criar conta no Supabase** → Connect → Session pooler → copiar o endereço.
  - **Criar conta no Render** → botão "Deploy" com o `render.yaml` → colar as chaves.
  - **Telegram**: BotFather → criar bot → copiar o token.
- Cada etapa com **imagens passo a passo** (prints marcados com setas), texto curto e botão "Copiar".
- O app confere cada etapa (testa a chave, o banco, o bot) e só libera a próxima quando a atual deu certo.

## O que falta construir

1. **Assistente de configuração** no app (telas por etapa, imagens, botões que abrem os sites, conferência automática).
2. **Deploy de 1 clique** no Render: repositório modelo público (sem segredos) + link `render.com/deploy?repo=...`.
3. **Código de acesso**: servidor de licenças (pequeno) que gera, envia por e-mail e valida o código. O app guarda a licença no Keystore.
4. **Site de vendas** com pagamento e e-mail automático.
5. **Painel do dono**: códigos vendidos, ativos, bloqueados.

## Ordem sugerida

1. Usar o app por algumas semanas e juntar resultados (prova para vender).
2. Assistente de configuração com imagens.
3. Deploy de 1 clique.
4. Código de acesso + site de vendas.
