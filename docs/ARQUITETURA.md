# Arquitetura

```
Celular (Android)                         Backend (Node/TS)                 Serviços
┌──────────────────────────┐   HTTPS +    ┌────────────────────────┐
│ Compose (telas)          │   Bearer     │ Fastify + zod          │──► Anthropic (textos)
│ ViewModel / Repositório  │ ───────────► │ Limites, logs sem      │──► Shopee Afiliados (GraphQL)
│ Room (catálogo offline)  │              │ segredo, token         │──► Mercado Livre (/items)
│ WorkManager (agenda)     │              │ Conferência dos textos │──► Telegram Bot API
│ Keystore (token cifrado) │              └────────────────────────┘
└──────────────────────────┘
        │ compartilhar
        ▼
   WhatsApp (você envia)
```

## Android (`android/app/src/main/java/bot/afiliados`)

- `domain/`: regras puras e testadas: links, preços, validade, desconto, máquina de estados da fila e montagem da mensagem.
- `data/`: Room (entidades e DAO), cliente do backend, cofre (Keystore), repositório e backup.
- `work/`: `PublicarWorker`, que publica no Telegram ou avisa por notificação.
- `ui/`: telas Início, Produtos, Novo/Editar produto, Detalhe, Criar oferta, Nichos, Fila, Revisão, Comissões e Ajustes.
- Sem Hilt: um contêiner simples em `AfiliadosApp` basta para um app pessoal.

## Estados da fila

```
RASCUNHO → AGUARDANDO_REVISAO → PRONTO → AGENDADO → EM_PROCESSAMENTO → PUBLICADO_CONFIRMADO
                 │                 │         │              ├──► ENVIO_NAO_CONFIRMADO → (tentar de novo, mesma chave)
                 └► AGUARDANDO_VERIFICACAO_PRECO            └──► FALHA → PRONTO
PRONTO/AGENDADO ──(você confirma que enviou)──► PUBLICADO_INFORMADO
qualquer estado não final ──► CANCELADO
```

- `PUBLICADO_CONFIRMADO` só vem com o ID da mensagem devolvido pelo serviço.
- Se o app fechar no meio do envio, o item vira `ENVIO_NAO_CONFIRMADO` quando o app abre de novo, e nunca "publicado".
- Preço vencido ou não verificado impede a publicação. A saída é verificar de novo ou confirmar o preço à mão.

## Preço

`NUNCA_VERIFICADO`, `VERIFICADO` (fonte oficial), `VERIFICADO_MANUAL` (você conferiu), `POSSIVELMENTE_DESATUALIZADO` (passou da validade, que vem de Ajustes), `VERIFICACAO_INDISPONIVEL`, `PRODUTO_INDISPONIVEL` (só quando a fonte confirma) e `LINK_INVALIDO`.
Uma consulta que falha não apaga o preço salvo e também não renova a validade.

## Testes

- Backend: `npm test`, com 42 testes (links e redirecionamentos, assinatura da Shopee, erros e acesso, renovação do token do ML, conferência dos textos da IA, limite diário, token obrigatório, corpo grande e Telegram sem duplicar, inclusive depois de reiniciar).
- Android: `./gradlew :app:testDebugUnitTest`, com 41 testes (regras, cliente HTTPS, backup, repositório com Room real, fluxo de publicação e abertura das telas com Robolectric).
