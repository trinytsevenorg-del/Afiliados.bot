package bot.afiliados.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bot.afiliados.AfiliadosApp
import bot.afiliados.BuildConfig
import bot.afiliados.data.FalhaLicenca
import bot.afiliados.data.NOMES_PLANOS
import bot.afiliados.data.SituacaoLicenca
import bot.afiliados.data.VersaoApp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val fmtData = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault())
fun dataCurta(ms: Long) = fmtData.format(Instant.ofEpochMilli(ms))

/** Tela que aparece antes do app quando falta licença, ela venceu ou o app é mais novo que o plano. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaLicenca(app: AfiliadosApp) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val situacao = app.licenca.value
    var porCodigo by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var pedido by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf(app.licencas.dados()?.c.orEmpty()) }
    var ativando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    fun ativar(cod: String? = null) {
        ativando = true; erro = null
        escopo.launch {
            try {
                val a = if (cod != null || porCodigo) app.licencas.ativar(cod ?: codigo, null, null) else app.licencas.ativar(null, email, pedido)
                app.licenca.value = app.situacaoLicenca()
                ctx.avisar("Licença ${a.nomePlano} ativada")
            } catch (e: FalhaLicenca) {
                erro = e.message
            } finally {
                ativando = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Afiliados.bot", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        when (situacao) {
            SituacaoLicenca.Adulterado -> {
                Aviso("Este app foi modificado ou não é o original. Por segurança, ele não abre. Baixe o app oficial.", erro = true)
                Button(onClick = { ctx.abrirLink(app.licencas.linkSite()) }) { Text("Baixar o app oficial") }
            }
            is SituacaoLicenca.Vencida -> {
                Aviso("Sua assinatura ${NOMES_PLANOS[situacao.dados.p] ?: ""} venceu em ${situacao.dados.e?.let(::dataCurta)}. Renove para continuar. Seus produtos e a fila continuam salvos.", erro = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { ctx.abrirLink(app.licencas.linkSite()) }) { Text("Renovar") }
                    OutlinedButton(enabled = !ativando, onClick = { ativar(situacao.dados.c) }) { Text("Já renovei") }
                }
            }
            is SituacaoLicenca.VersaoForaDoPlano -> {
                Aviso("Esta versão do app saiu depois do fim das atualizações do seu plano ${NOMES_PLANOS[situacao.dados.p] ?: ""} (até ${dataCurta(situacao.dados.u)}). Instale a versão do seu plano ou passe para o Premium.", erro = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { ctx.abrirLink(app.licencas.linkBaixar(situacao.dados.u)) }) { Text("Baixar a versão do meu plano") }
                    OutlinedButton(onClick = { ctx.abrirLink(app.licencas.linkSite()) }) { Text("Ver planos") }
                    TextButton(enabled = !ativando, onClick = { ativar(situacao.dados.c) }) { Text("Já fiz upgrade") }
                }
            }
            else -> {
                Text("Ative sua licença", style = MaterialTheme.typography.titleLarge)
                Text("Use o e-mail da compra e o número do pedido (está no e-mail da Kiwify ou da Hotmart). Se recebeu um código, toque em \"Tenho um código\".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(!porCodigo, { porCodigo = false }, label = { Text("E-mail e pedido") })
                    Chip(porCodigo, { porCodigo = true }, label = { Text("Tenho um código") })
                }
                if (porCodigo) Campo(codigo, { codigo = it.uppercase() }, "Código (ABOT-XXXX-XXXX-XXXX)")
                else {
                    Campo(email, { email = it }, "E-mail da compra")
                    Campo(pedido, { pedido = it }, "Número do pedido")
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !ativando && (if (porCodigo) codigo.length >= 10 else email.contains('@') && pedido.isNotBlank()),
                    onClick = { ativar() },
                ) { Text(if (ativando) "Ativando… (pode levar 1 minuto)" else "Ativar") }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { ctx.abrirLink(app.licencas.linkSite()) }) { Text("Ainda não comprei: ver planos") }
            }
        }
        if (ativando) CircularProgressIndicator()
        erro?.let { Aviso(it, erro = true) }
        Text("Versão ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Ajustes: plano, validade e atualização do app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecaoLicenca(app: AfiliadosApp) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val d = app.licencas.dados()
    var nova by remember { mutableStateOf<VersaoApp?>(null) }
    var procurando by remember { mutableStateOf(false) }
    var procurou by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { nova = app.licencas.versaoNova(); procurou = true }

    Secao("Licença e atualizações") {
        if (d == null) Text(if (BuildConfig.EXIGIR_LICENCA) "Sem licença ativa." else "Versão de desenvolvimento (não pede licença).", style = MaterialTheme.typography.bodySmall)
        else {
            Text("Plano ${NOMES_PLANOS[d.p] ?: d.p} · ${d.c}")
            Text(
                (d.e?.let { "Válido até ${dataCurta(it)}" } ?: "Vitalício") + " · " +
                    (if (d.p == "essencial") "sem atualizações" else "atualizações até ${dataCurta(d.u)}"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("App versão ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        val v = nova
        when {
            v != null && v.versionCode > BuildConfig.VERSION_CODE -> {
                val coberta = !BuildConfig.EXIGIR_LICENCA || (d != null && d.u >= v.data)
                Aviso("Versão ${v.versao} disponível" + if (v.novidades.isNotEmpty()) ":\n• " + v.novidades.joinToString("\n• ") else "")
                if (coberta) Button(onClick = { ctx.abrirLink(app.licencas.linkBaixar()) }) { Text("Baixar e instalar a ${v.versao}") }
                else {
                    Text("Essa versão não está no seu plano.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { ctx.abrirLink(app.licencas.linkSite()) }) { Text("Passar para o Premium") }
                }
            }
            procurou && v != null -> Text("Você está na versão mais nova.", style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !procurando, onClick = {
                procurando = true
                escopo.launch { nova = app.licencas.versaoNova(); procurou = true; procurando = false; if (nova == null) ctx.avisar("Não deu para consultar agora") }
            }) { Text(if (procurando) "Procurando…" else "Procurar atualização") }
            if (d != null) TextButton(onClick = { escopo.launch {
                try { app.licencas.ativar(d.c, null, null); app.licenca.value = app.situacaoLicenca(); ctx.avisar("Licença conferida") }
                catch (e: FalhaLicenca) { ctx.avisar(e.message ?: "Falha") }
            } }) { Text("Conferir licença") }
        }
    }
}
