# Afiliados.bot

## Estilo de resposta
- Português do Brasil, direto ao resultado.
- Frases curtas. Sem enrolação.

## Regras do projeto
- Projeto separado da BattleHub. Nada daqui vai para o repositório, o mapa ou o banco da BattleHub, e vice-versa.
- Nunca inventar dados: preço, desconto, comissão, venda, estoque ou link de afiliado sem fonte.
- Chaves só no backend (`backend/.env`). Nunca no APK.
- "Publicado" só com confirmação do serviço ou do dono.
- Antes de push: `cd backend && npm run typecheck && npm test` e `cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug`.
- Integrações e o que falta: `docs/INTEGRACOES.md`.
