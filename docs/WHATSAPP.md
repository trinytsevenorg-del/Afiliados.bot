# WhatsApp automático (número próprio)

O backend conecta um número seu, como o WhatsApp Web faz.
Ele posta sozinho nos **seus** grupos, um por vez, com intervalo.

> Não é a API oficial do WhatsApp. Funciona como Z-API e Evolution.
> Há risco de banimento do número. Use um chip só para isso.
> Nunca use o seu número pessoal.

## Limites de segurança (já ligados)

- Só envia para grupos (`@g.us`). Nunca para contatos.
- Mínimo de 10 minutos entre mensagens no mesmo grupo (`WA_INTERVALO_MIN`).
- Máximo de 40 mensagens por dia em cada grupo (`WA_MAX_DIA`).
- Mostra "digitando…" por 2 a 6 segundos antes de enviar.
- A mesma oferta nunca sai duas vezes no mesmo grupo.

## Passo 1: banco grátis (Supabase)

A sessão do WhatsApp precisa ficar salva. O Render grátis apaga os arquivos ao dormir.

1. Entre em supabase.com e crie um projeto novo (plano Free).
2. Anote a senha do banco.
3. Clique em **Connect** no topo.
4. Escolha **Session pooler** e copie o endereço `postgresql://...`.
5. Troque `[YOUR-PASSWORD]` pela senha.

## Passo 2: Render

1. Abra o serviço no Render → **Environment**.
2. Adicione `DATABASE_URL` com o endereço do passo 1.
3. Salve. O Render faz o deploy de novo.

## Passo 3: manter acordado

Automático. No Render, o servidor visita a si mesmo a cada 10 minutos (`RENDER_EXTERNAL_URL`).
Fora do Render, defina `KEEPALIVE_URL` com o endereço público. O cron-job.org virou opcional.

## Passo 4: chip e conexão

1. Coloque o chip novo num celular. Instale o WhatsApp Business.
2. Use o número normalmente por alguns dias antes de automatizar.
3. No app Afiliados.bot: **Ajustes → WhatsApp automático**.
4. Digite o número com 55 e DDD. Toque em **Gerar código**.
5. No WhatsApp do chip: **Aparelhos conectados → Conectar aparelho → Conectar com número de telefone**.
6. Digite o código de 8 letras.

## Passo 5: grupos e canais

1. Crie um grupo por nicho com o chip (ex.: "Achadinhos Casa").
2. Em **Configurações do grupo**, deixe só administradores enviarem.
3. No app: **Ajustes → Canais → Novo canal → WhatsApp (automático)**.
4. Toque em **Escolher grupo**. Salve.
5. Em **Nichos**, escolha esse canal como padrão do nicho.

## Passo 6: postar

- Crie as ofertas (link ou prints).
- Na **Fila**, toque em **🚀 Publicar todos**.
- O app agenda uma por vez, no intervalo de Ajustes (15 ou 30 min).
- Quem posta é o servidor (fila no banco). Pode desligar o celular. Ao abrir o app, ele mostra o que já saiu.

## Se cair

- "Desconectado": gere um código novo em Ajustes.
- "Limite": o grupo já recebeu muito. Espere.
- Número banido: troque o chip. Os grupos e canais continuam no app.
