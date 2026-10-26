# Afiliados.bot

App Android **privado** para organizar produtos de afiliado, criar textos com IA e publicar ofertas.
Uso pessoal, uma pessoa só. Sem cadastro, sem planos e sem multiusuário.

Projeto separado da BattleHub. Não compartilha código, banco nem contas.

## O que tem aqui

| Pasta | O quê |
|---|---|
| `android/` | App Kotlin + Jetpack Compose (Material 3), Room, WorkManager |
| `backend/` | Node.js + TypeScript (Fastify). Guarda as chaves e conversa com IA, Shopee, Mercado Livre e Telegram |
| `docs/` | Integrações, arquitetura e decisões |

## O que funciona de verdade

| Função | Situação |
|---|---|
| Catálogo local (offline), nichos editáveis, busca e filtros | Funciona |
| Cadastro por link (colar ou "Compartilhar" de outro app) | Funciona; valida HTTPS e o domínio, e segue links curtos pelo backend |
| Cadastro pelo **print do anúncio** (IA lê nome, preço, % de ganho, características) | Funciona com `GEMINI_API_KEY` (grátis) ou `ANTHROPIC_API_KEY` |
| Cadastro manual quando a consulta falha | Funciona |
| Detecção de produto repetido | Funciona (pelo código do produto) |
| Textos com IA (7 versões + hashtags), editáveis, com histórico | Funciona com `GEMINI_API_KEY` (grátis) ou `ANTHROPIC_API_KEY` no backend |
| Bloqueio de texto enganoso (preço inventado, "mais barato", falsa escassez, link no texto) | Funciona (conferência no backend) |
| Verificação de preço, validade e histórico | Shopee e ML: com credenciais. Sem elas: "Verificação indisponível" ou confirmação manual |
| Fila com estados, revisão, agendamento e tentativas | Funciona |
| Publicação automática no **Telegram**, com confirmação e sem repetir | Funciona com `TELEGRAM_BOT_TOKEN` |
| **WhatsApp** | Compartilhamento do Android + "Você publicou?". Envio automático para grupos não existe pela via oficial |
| Comissões (estimada → registrada → confirmada → paga) | Registro manual |
| Backup JSON (exportar/importar sem duplicar) | Funciona |
| Bloqueio por digital/senha do aparelho | Opcional, em Ajustes |

**Começando do zero:** [`docs/PRIMEIROS-PASSOS.md`](docs/PRIMEIROS-PASSOS.md). Passo a passo de uso: [`docs/COMO-USAR.md`](docs/COMO-USAR.md). O que depende de credencial ou aprovação: [`docs/INTEGRACOES.md`](docs/INTEGRACOES.md).

## Rodar o backend

```bash
cd backend
cp .env.example .env      # preencha APP_TOKEN e as chaves que tiver
npm ci
npm test                  # 44 testes
npm run build && npm start
```

O app só aceita backend em **HTTPS**. Opções:
- Render: já tem `render.yaml` (New → Blueprint). Guia: `docs/PRIMEIROS-PASSOS.md`.
- Railway ou Fly.io com o `Dockerfile` (monte um volume em `/data`).
- Seu computador + Cloudflare Tunnel (`cloudflared tunnel --url http://localhost:8787`) para testes.

## Gerar o APK

```bash
cd android
./gradlew :app:testDebugUnitTest   # 46 testes
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

O GitHub Actions também gera o APK a cada push (aba Actions → artefato `afiliados-bot-debug-apk`).

No celular: abra o APK, permita "instalar apps desconhecidos" e instale. Depois, em **Ajustes**, informe o endereço do backend e o `APP_TOKEN`.

## Segurança

- Nenhuma chave de IA, Shopee, Mercado Livre ou Telegram no APK. Ficam só no `.env` do backend.
- O app guarda o `APP_TOKEN` cifrado com o Android Keystore.
- O app só usa HTTPS, e o backup do Android fica desligado.
- O backend exige o token em todas as rotas (menos `/api/health`), limita requisições por minuto, gerações de IA por dia e o tamanho do corpo, e não registra tokens nos logs.
