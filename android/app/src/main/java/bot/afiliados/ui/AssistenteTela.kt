package bot.afiliados.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import bot.afiliados.AfiliadosApp
import bot.afiliados.R
import bot.afiliados.data.Assistente
import bot.afiliados.data.FalhaBackend
import kotlinx.coroutines.launch

private const val TOTAL = 6

/**
 * Configuração guiada, do zero: Telegram, Gemini, Supabase e Render.
 * Cada etapa tem botão que abre o site certo, imagem do que tocar e teste na hora.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistenteTela(app: AfiliadosApp, nav: NavHostController) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val a = remember { Assistente(app.cofre) }
    var etapa by rememberSaveable { mutableStateOf(0) }
    var testando by remember { mutableStateOf(false) }
    var msg by remember(etapa) { mutableStateOf<Pair<String, Boolean>?>(null) }

    fun testar(bloco: suspend () -> Pair<String, Boolean>) {
        testando = true; msg = null
        escopo.launch { msg = bloco(); testando = false }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Configuração guiada") }, navigationIcon = {
            IconButton(onClick = { if (etapa > 0) etapa-- else sair(app, nav) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
        })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            LinearProgressIndicator(progress = { (etapa + 1f) / (TOTAL + 1) }, modifier = Modifier.fillMaxWidth())
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (etapa > 0) Text("Etapa $etapa de $TOTAL", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                when (etapa) {
                    0 -> {
                        Titulo("Vamos deixar tudo pronto")
                        Text("Leva uns 15 minutos, só pelo celular. Você vai criar 4 contas grátis e colar o que o app pedir. O app testa cada etapa antes de seguir.")
                        Passo(1, "Telegram: o robô que posta nos seus canais.")
                        Passo(2, "Google Gemini: a IA que lê os prints e escreve as ofertas.")
                        Passo(3, "Supabase: o banco que guarda a fila e o WhatsApp.")
                        Passo(4, "Render: o servidor que posta mesmo com o celular desligado.")
                        Aviso("Nenhuma dessas contas pede cartão no plano grátis. Se alguma pedir, pare e fale com o suporte.")
                    }
                    1 -> {
                        Titulo("Crie o robô do Telegram")
                        BotaoAbrir("Abrir o BotFather no Telegram", "https://t.me/BotFather")
                        Passo(1, "Toque em Iniciar (ou envie /start).")
                        Passo(2, "Envie /newbot.")
                        Passo(3, "Mande o nome do robô. Ex.: Achadinhos da Ana.")
                        Passo(4, "Mande o usuário, terminando em bot. Ex.: achadinhosdaana_bot.")
                        Passo(5, "Ele responde com um token (números:letras). Toque nele para copiar e cole aqui.")
                        Imagem(R.drawable.passo_telegram_botfather)
                        var token by remember { mutableStateOf(a.telegram.orEmpty()) }
                        Campo(token, { token = it.trim() }, "Token do robô", segredo = true)
                        Button(enabled = token.isNotBlank() && !testando, onClick = {
                            testar { a.testarTelegram(token).fold({ a.telegram = token; "Robô $it conferido ✅" to true }, { (it.message ?: "Falhou") to false }) }
                        }) { Text("Testar e salvar") }
                    }
                    2 -> {
                        Titulo("Pegue a chave grátis da IA")
                        BotaoAbrir("Abrir o Google AI Studio", "https://aistudio.google.com/apikey")
                        Passo(1, "Entre com sua conta Google e aceite os termos.")
                        Passo(2, "Toque em Create API key (Criar chave de API).")
                        Imagem(R.drawable.passo_gemini_chaves)
                        Passo(3, "Se pedir um projeto, escolha o que aparecer ou crie um. Toque em Criar chave.")
                        Imagem(R.drawable.passo_gemini_criar)
                        Passo(4, "Copie a chave (começa com AIza) e cole aqui.")
                        var chave by remember { mutableStateOf(a.gemini.orEmpty()) }
                        Campo(chave, { chave = it.trim() }, "Chave do Gemini (AIza...)", segredo = true)
                        Button(enabled = chave.isNotBlank() && !testando, onClick = {
                            testar { a.testarGemini(chave).fold({ a.gemini = chave; "Chave conferida ✅" to true }, { (it.message ?: "Falhou") to false }) }
                        }) { Text("Testar e salvar") }
                    }
                    3 -> {
                        Titulo("Crie o banco (Supabase)")
                        var senha by remember { mutableStateOf(a.senhaBanco ?: Assistente.gerar(20).also { a.senhaBanco = it }) }
                        BotaoAbrir("Abrir o Supabase", "https://supabase.com/dashboard/new")
                        Passo(1, "Toque em Start your project e crie a conta (GitHub ou e-mail).")
                        Imagem(R.drawable.passo_supabase_inicio)
                        Passo(2, "Em New project: dê um nome (ex.: afiliados) e cole a senha abaixo em Database Password.")
                        CampoCopiar("Senha do banco", senha)
                        Passo(3, "Region: escolha East US (Ohio) ou South America (São Paulo). Toque em Create new project e espere 2 minutos.")
                        Passo(4, "No topo, toque em Connect. Em Connection Method, escolha Session pooler e copie o endereço.")
                        Imagem(R.drawable.passo_supabase_connect)
                        Passo(5, "Cole aqui. Pode colar com [YOUR-PASSWORD]: o app troca pela senha.")
                        var colado by remember { mutableStateOf(a.banco.orEmpty()) }
                        Campo(colado, { colado = it }, "Endereço (postgresql://...)", linhas = 3)
                        Button(enabled = colado.isNotBlank(), onClick = {
                            Assistente.montarBanco(colado, senha).fold(
                                { a.banco = it; colado = it; msg = "Endereço salvo ✅" to true },
                                { msg = (it.message ?: "Endereço inválido") to false },
                            )
                        }) { Text("Salvar") }
                        TextButton(onClick = { senha = Assistente.gerar(20); a.senhaBanco = senha }) { Text("Gerar outra senha") }
                    }
                    4 -> {
                        Titulo("Crie o servidor (Render)")
                        BotaoAbrir("Abrir o Render", "https://dashboard.render.com/register")
                        Passo(1, "Crie a conta grátis (GitHub, Google ou e-mail).")
                        Passo(2, "Toque em + New → Web Service.")
                        Imagem(R.drawable.passo_render_novo)
                        Passo(3, "Em Source Code, escolha Existing Image e cole este endereço:")
                        CampoCopiar("Imagem", Assistente.IMAGEM)
                        Passo(4, "Dê um nome (ex.: meus-achadinhos). Em Instance Type, escolha Free.")
                        Passo(5, "Em Environment Variables, toque em Add from .env e cole o bloco abaixo.")
                        if (a.telegram == null || a.gemini == null || a.banco == null) Aviso("Falta: " + listOfNotNull(
                            "Telegram".takeIf { a.telegram == null }, "Gemini".takeIf { a.gemini == null }, "banco".takeIf { a.banco == null },
                        ).joinToString(", ") + ". Volte e complete antes.", erro = true)
                        CampoCopiar("Bloco para o Render", a.blocoEnv(), segredo = true)
                        Imagem(R.drawable.passo_render_env)
                        Passo(6, "Toque em Deploy web service e espere aparecer Live (uns 3 minutos).")
                    }
                    5 -> {
                        Titulo("Conecte o app ao servidor")
                        Passo(1, "No Render, copie o endereço do serviço, logo abaixo do nome (termina em .onrender.com).")
                        var url by remember { mutableStateOf(app.prefs.backendUrl) }
                        Campo(url, { url = it.trim() }, "Endereço (https://...onrender.com)")
                        Button(enabled = url.startsWith("https://") && !testando, onClick = {
                            app.prefs.backendUrl = url
                            app.cofre.salvar(AfiliadosApp.TOKEN, a.appToken)
                            testar {
                                try {
                                    val s = app.backend.saude()
                                    if (s.banco == "erro") "Servidor no ar, mas o banco não conectou. Confira o DATABASE_URL no Render." to false
                                    else { app.backend.status(false); "Tudo conectado ✅" to true }
                                } catch (e: FalhaBackend) {
                                    "Ainda não respondeu: ${e.message}. Se acabou de criar, espere ficar Live e teste de novo." to false
                                }
                            }
                        }) { Text("Testar conexão") }
                    }
                    else -> {
                        Titulo("Pronto! 🎉")
                        Text("Agora é só criar onde as ofertas vão sair:")
                        Passo(1, "No Telegram, crie um canal (ex.: Achadinhos Casa). Abra o seu robô, toque no nome dele e em Adicionar ao Grupo ou Canal.")
                        Imagem(R.drawable.passo_telegram_adicionar)
                        Passo(2, "Escolha o canal e confirme como administrador.")
                        Imagem(R.drawable.passo_telegram_canal)
                        Passo(3, "No app: Ajustes → Canais de divulgação → Adicionar → Telegram. Coloque o @ do canal e toque em Testar destino.")
                        Passo(4, "Em Nichos, marque o canal de cada nicho.")
                        Passo(5, "Mande um print em Produtos → Novo, cole o seu link de afiliado e toque em Publicar todos na Fila.")
                        Aviso("WhatsApp automático (opcional): Ajustes → WhatsApp automático. Use um chip só para isso.")
                        Button(onClick = { sair(app, nav, "ajustes") }) { Text("Ir para os canais") }
                    }
                }
                if (testando) CircularProgressIndicator()
                msg?.let { (t, ok) -> Aviso(t, erro = !ok) }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (etapa > 0) OutlinedButton(onClick = { etapa-- }) { Text("Voltar") } else TextButton(onClick = { sair(app, nav) }) { Text("Já sei, pular") }
                if (etapa <= TOTAL - 1) Button(onClick = { etapa++ }) { Text(if (etapa == 0) "Começar" else "Próximo") }
            }
        }
    }
}

/** Sai do assistente. Se ele foi a primeira tela, vai para o Início (ou [destino]). */
private fun sair(app: AfiliadosApp, nav: NavHostController, destino: String? = null) {
    app.prefs.assistenteVisto = true
    if (nav.previousBackStackEntry != null) nav.popBackStack()
    else nav.navigate("inicio") { popUpTo("assistente") { inclusive = true } }
    destino?.let { nav.irPara(it) }
}

@Composable
private fun Titulo(t: String) = Text(t, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

@Composable
private fun Passo(n: Int, texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Text("$n", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Text(texto, Modifier.weight(1f))
    }
}

@Composable
private fun BotaoAbrir(rotulo: String, url: String) {
    val ctx = LocalContext.current
    Button(modifier = Modifier.fillMaxWidth(), onClick = { ctx.abrirLink(url) }) { Text(rotulo) }
}

@Composable
private fun Imagem(@DrawableRes id: Int) {
    Image(
        painterResource(id), contentDescription = null, contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
    )
}

@Composable
private fun CampoCopiar(rotulo: String, valor: String, segredo: Boolean = false) {
    val ctx = LocalContext.current
    var ver by remember { mutableStateOf(!segredo) }
    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(rotulo, style = MaterialTheme.typography.labelLarge)
            Text(if (ver) valor else "•".repeat(minOf(valor.length, 24)), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ctx.copiar(rotulo, valor); ctx.avisar("Copiado") }) { Text("Copiar") }
                if (segredo) TextButton(onClick = { ver = !ver }) { Text(if (ver) "Esconder" else "Mostrar") }
            }
        }
    }
}
