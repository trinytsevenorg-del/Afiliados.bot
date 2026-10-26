# Como usar o Afiliados.bot

## Ideia geral

1. Você junta produtos no **catálogo**.
2. Cria uma **oferta**: texto da IA + seu link de afiliado.
3. Põe na **fila** e publica: no Telegram sai sozinho, no WhatsApp você envia.
4. Anota as **comissões** que aparecem no painel da loja.

## Parte 1 · Deixar pronto (uma vez só)

1. **Instale o APK.** Abra o arquivo no celular e permita "instalar apps desconhecidos".
2. **Suba o backend.** É ele que guarda as chaves (IA, Shopee, Telegram). Sem ele o app funciona, mas só no modo manual. Veja o README.
3. **No app, abra Ajustes → Backend.** Cole o endereço (https://…) e o `APP_TOKEN`. Toque em Salvar e depois em Testar conexão.
4. **Ajustes → Integrações → Testar todas.** Cada uma mostra se está pronta ou o que falta.
5. **Ajustes → Canais.** Cadastre onde você divulga:
   - "WhatsApp (compartilhar)": para seus grupos.
   - "Telegram (automático)": `@seucanal` com o bot como administrador.
6. **Nichos:** já vêm 8. Toque num nicho para mudar nome, ícone, tom do texto e canal padrão.

## Parte 2 · Cadastrar um produto

### Jeito mais rápido: pelo print (precisa da IA ligada)

1. No app da loja, abra o produto e tire **print** da tela. A barra de afiliado com o seu **% de ganho** e o **preço** precisam aparecer. Se as características estiverem mais embaixo, tire um segundo print.
2. Na galeria, toque em **Compartilhar → Afiliados.bot**. Ou, no app: **Novo produto → 📷 Preencher pelo print** e escolha até 10 imagens.
3. A IA preenche nome, preço, preço riscado, % de ganho e características. **Só o que está escrito no print.** O que não aparece fica vazio.
4. **Link de afiliado:** no app da loja, toque em **GANHOS** → copiar link. No Afiliados.bot, toque em **📋 Colar link copiado**.
5. **Foto:** a IA acha a foto do produto no print e o app recorta sozinho. Se não gostar, toque em **Trocar foto** e escolha outra da galeria.
6. Escolha o **nicho**, confira e salve.
7. Se você não mexer no preço lido, ele já fica como **"Confirmado por você"**. Assim não precisa confirmar de novo para publicar.

A comissão estimada sai sozinha: preço × % de ganho (ex.: R$ 100 × 17% = R$ 17). Ela é **estimativa**. O valor real só vale quando a loja confirmar.

### Pelo link

1. Na Shopee ou no Mercado Livre, abra o produto e toque em **Compartilhar → Afiliados.bot**. Ou, no app: **Início → Novo produto** e cole o link.
2. Toque em **Analisar**. O app confere o link, abre links curtos e busca os dados oficiais (quando a integração está ligada).
3. Revise os campos e complete o que faltar. O que não veio fica vazio: o app nunca inventa.
4. **Link de afiliado:**
   - Shopee: toque em "Gerar link pela API da Shopee" (precisa de acesso à API) ou cole o link do painel.
   - Mercado Livre: gere no painel de Afiliados do ML e cole.
5. Em **Características**, escreva fatos do produto, um por linha (ex.: "Bateria de 30 horas"). A IA só usa isso.
6. **Salvar no catálogo.** Se o produto já existir, o app avisa.

## Parte 3 · Criar a oferta

1. Abra o produto → **Criar oferta / texto**.
2. Escolha o canal e toque em **Gerar com IA**.
3. Troque entre as versões: WhatsApp, Canal, Objetiva, Chamativa e Completa.
4. Edite o texto à vontade. "Gerar outra versão" não apaga as anteriores.
5. Se aparecer aviso vermelho (ex.: "número sem base"), corrija antes de usar.
6. A prévia mostra como vai sair. O link entra no final, sem mudar nada.
7. **Adicionar à fila.**

## Parte 4 · Publicar

Na **Fila**, toque no item para revisar.

1. **Revisado, deixar pronto.** Se o preço estiver vencido, o app pede para verificar antes:
   - "Verificar preço agora" (pela API), ou
   - "Confirmar à mão": você abre a loja, vê o preço e digita.
2. Depois:
   - **Telegram:** "Publicar agora" ou "Agendar". Só fica "Publicado com confirmação" quando o Telegram confirma.
   - **WhatsApp:** "Abrir no WhatsApp" → escolha o grupo → a **foto vai junto com o texto** → envie → volte e responda **"Sim, enviei"**.
   - **Agendado no WhatsApp:** na hora chega uma notificação "Hora de publicar". Toque e envie.
3. Se aparecer **"Envio não confirmado"**, olhe o canal. Se a mensagem está lá, toque em "Apareceu no canal". Se não está, toque em "Tentar de novo" (não duplica).

## Parte 5 · Comissões

Ajustes → **Abrir comissões** → Registrar. Use o painel de afiliado da loja como fonte.

| Status | Quando usar |
|---|---|
| Estimada | Só uma conta: preço × comissão |
| Registrada | O painel mostrou a venda, mas ainda não validou |
| Confirmada | A loja validou a comissão |
| Paga | O dinheiro caiu |

## Rotina sugerida (10 minutos por dia)

1. Cadastre 3 a 5 produtos novos pelo "Compartilhar".
2. Gere os textos e mande para a fila.
3. Verifique os preços dos itens prontos.
4. Agende no Telegram e envie no WhatsApp.
5. Uma vez por semana, registre as comissões do painel.

## Cores do app

Mesmo visual da BattleHub: fundo preto, cartões cinza e dourado só no que importa. Em Ajustes → Aparência você escolhe Sistema, Preto ou Branco.
