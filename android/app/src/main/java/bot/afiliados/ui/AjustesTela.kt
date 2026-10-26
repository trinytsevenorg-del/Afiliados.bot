package bot.afiliados.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import bot.afiliados.AfiliadosApp
import bot.afiliados.data.Canal
import bot.afiliados.data.FalhaBackend
import bot.afiliados.data.StatusIntegracao
import bot.afiliados.domain.TipoCanal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun rotuloEstado(e: String) = when (e) {
    "nao_configurado" -> "Não configurado"
    "configurado" -> "Configurado"
    "aguardando_autorizacao" -> "Aguardando autorização"
    "erro_autenticacao" -> "Erro de autenticação"
    "indisponivel" -> "Indisponível para essa operação"
    else -> e
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AjustesTela(app: AfiliadosApp, nav: NavHostController) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val canais by app.repo.dao.canais().collectAsStateWithLifecycle(emptyList())
    var url by remember { mutableStateOf(app.prefs.backendUrl) }
    var token by remember { mutableStateOf("") }
    val temToken = remember { mutableStateOf(app.cofre.ler(AfiliadosApp.TOKEN) != null) }
    var validade by remember { mutableStateOf(app.prefs.validadePrecoHoras.toString()) }
    var subId by remember { mutableStateOf(app.prefs.subIdPadrao) }
    var bloqueio by remember { mutableStateOf(app.prefs.bloqueioLocal) }
    var status by remember { mutableStateOf<List<StatusIntegracao>?>(null) }
    var statusErro by remember { mutableStateOf<String?>(null) }
    var carregando by remember { mutableStateOf(false) }
    var editandoCanal by remember { mutableStateOf<Canal?>(null) }

    fun consultar(testar: Boolean) {
        carregando = true; statusErro = null
        escopo.launch {
            try { status = app.backend.status(testar).integracoes } catch (e: FalhaBackend) { status = null; statusErro = e.message }
            carregando = false
        }
    }

    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) escopo.launch {
            try {
                val json = app.backup.exportar()
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
                app.repo.registrar("backup", "Exportou backup")
                ctx.avisar("Backup salvo")
            } catch (e: Exception) { ctx.avisar("Falha ao exportar: ${e.message}") }
        }
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) escopo.launch {
            try {
                val texto = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } ?: error("Arquivo vazio")
                val r = app.backup.importar(texto)
                ctx.avisar("Importados: ${r.produtos} produtos, ${r.nichos} nichos, ${r.comissoes} comissões. ${r.repetidos} já existiam e foram pulados.")
            } catch (e: Exception) { ctx.avisar(e.message ?: "Falha ao importar") }
        }
    }

    LazyColumn(contentPadding = espaco, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TopAppBar(title = { Text("Ajustes") }) }
        item {
            Secao("Backend") {
                Text("O backend guarda as chaves de IA, Shopee, Mercado Livre e Telegram. Elas nunca ficam no app.", style = MaterialTheme.typography.bodySmall)
                Campo(url, { url = it }, "Endereço (https://...)")
                Campo(token, { token = it }, if (temToken.value) "Token do app (salvo; digite para trocar)" else "Token do app", ajuda = "O mesmo APP_TOKEN do .env do backend", segredo = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (url.isNotBlank() && !url.trim().startsWith("https://")) { ctx.avisar("Use um endereço HTTPS"); return@Button }
                        app.prefs.backendUrl = url
                        if (token.isNotBlank()) { app.cofre.salvar(AfiliadosApp.TOKEN, token.trim()); token = ""; temToken.value = true }
                        ctx.avisar("Salvo")
                        consultar(false)
                    }) { Text("Salvar") }
                    OutlinedButton(onClick = { escopo.launch {
                        try { app.backend.saude(); ctx.avisar("Backend respondeu") } catch (e: FalhaBackend) { ctx.avisar(e.message ?: "Falha") }
                    } }) { Text("Testar conexão") }
                    if (temToken.value) TextButton(onClick = { app.cofre.salvar(AfiliadosApp.TOKEN, null); temToken.value = false }) { Text("Apagar token", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item {
            Secao("Integrações", acao = { TextButton(onClick = { consultar(true) }, enabled = !carregando) { Text(if (carregando) "Testando…" else "Testar todas") } }) {
                if (status == null && statusErro == null) TextButton(onClick = { consultar(false) }) { Text("Ver estado das integrações") }
                statusErro?.let { Aviso(it, erro = true) }
                status?.forEach { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.nome, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Selo(rotuloEstado(s.estado), if (s.estado == "configurado") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                        }
                        Text(s.detalhe, style = MaterialTheme.typography.bodySmall)
                        s.operacoes.forEach { o ->
                            Text("${if (o.disponivel) "✓" else "✗"} ${o.descricao}" + (o.motivo?.let { " — $it" } ?: ""), style = MaterialTheme.typography.bodySmall,
                                color = if (o.disponivel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (s.estado != "configurado") s.comoConfigurar?.let { Text("Como ativar: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        HorizontalDivider(Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        item {
            Secao("Canais de divulgação", acao = { TextButton(onClick = { editandoCanal = Canal(nome = "", tipo = TipoCanal.WHATSAPP_COMPARTILHAR) }) { Text("Adicionar") } }) {
                if (canais.isEmpty()) Text("Nenhum canal. Sem canal, você escolhe o destino na hora de compartilhar.", style = MaterialTheme.typography.bodySmall)
                canais.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { editandoCanal = c }.padding(vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(c.nome, fontWeight = FontWeight.SemiBold)
                            Text(c.tipo.rotulo + (if (c.destino.isNotBlank()) " · ${c.destino}" else "") + (c.ultimoTeste?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                        }
                        if (!c.ativo) Selo("Pausado", MaterialTheme.colorScheme.outline)
                    }
                }
                Text("WhatsApp: o envio é sempre feito por você pelo compartilhamento. Abrir o WhatsApp não conta como publicado.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Secao("Preços e links") {
                Campo(validade, { validade = it.filter(Char::isDigit).take(3) }, "Validade do preço (horas)", numero = true, ajuda = "Depois disso, o preço precisa ser verificado de novo")
                Campo(subId, { subId = it }, "Sub ID nos links da Shopee", ajuda = "Ajuda a saber de onde veio a venda no painel da Shopee")
                Button(onClick = {
                    app.prefs.validadePrecoHoras = validade.toIntOrNull() ?: 6
                    app.prefs.subIdPadrao = subId
                    validade = app.prefs.validadePrecoHoras.toString(); subId = app.prefs.subIdPadrao
                    ctx.avisar("Salvo")
                }) { Text("Salvar") }
            }
        }
        item {
            var modelo by remember { mutableStateOf(app.prefs.modeloMensagem) }
            var cupomPadrao by remember { mutableStateOf(app.prefs.cupomPadrao) }
            Secao("Modelo da mensagem") {
                Text("Campos: {titulo} {de} {por} {link} {cupom}. Linha com campo vazio some sozinha. *negrito* e ~riscado~ funcionam no WhatsApp e no Telegram.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Campo(modelo, { modelo = it }, "Modelo", linhas = 10)
                Campo(cupomPadrao, { cupomPadrao = it }, "Cupom padrão (opcional)", ajuda = "Usado quando o produto não tem cupom próprio. Só código válido.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (!modelo.contains(bot.afiliados.domain.Mensagem.LINK)) { ctx.avisar("O modelo precisa ter {link}"); return@Button }
                        app.prefs.modeloMensagem = modelo; app.prefs.cupomPadrao = cupomPadrao; cupomPadrao = app.prefs.cupomPadrao
                        ctx.avisar("Modelo salvo")
                    }) { Text("Salvar") }
                    TextButton(onClick = { modelo = bot.afiliados.domain.Mensagem.MODELO_PADRAO }) { Text("Voltar ao padrão") }
                }
            }
        }
        item {
            Secao("Aparência") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("sistema" to "Sistema", "preto" to "Preto", "branco" to "Branco").forEach { (id, nome) ->
                        Chip(app.tema.value == id, { app.tema.value = id; app.prefs.tema = id }, label = { Text(nome) })
                    }
                }
            }
        }
        item {
            Secao("Segurança") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pedir digital ou senha do aparelho ao abrir", Modifier.weight(1f))
                    Switch(bloqueio, { bloqueio = it; app.prefs.bloqueioLocal = it })
                }
            }
        }
        item {
            Secao("Backup") {
                Text("Exporta catálogo, nichos, textos, canais e comissões em JSON. O token do backend não vai junto. A fila não é importada, para não repetir publicações.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportar.launch("afiliados-backup.json") }) { Text("Exportar") }
                    OutlinedButton(onClick = { importar.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Importar") }
                }
            }
        }
        item { OutlinedButton(onClick = { nav.irPara("comissoes") }, modifier = Modifier.fillMaxWidth()) { Text("Abrir comissões") } }
        item { Rodape() }
    }

    editandoCanal?.let { c ->
        EditorCanal(app, c, aoFechar = { editandoCanal = null }) { salvo, excluir ->
            escopo.launch {
                when {
                    excluir -> app.repo.dao.excluir(salvo)
                    salvo.id == 0L -> app.repo.dao.inserir(salvo)
                    else -> app.repo.dao.atualizar(salvo)
                }
                editandoCanal = null
            }
        }
    }
}

@Composable
private fun EditorCanal(app: AfiliadosApp, c: Canal, aoFechar: () -> Unit, aoSalvar: (Canal, Boolean) -> Unit) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    var nome by remember { mutableStateOf(c.nome) }
    var tipo by remember { mutableStateOf(c.tipo) }
    var destino by remember { mutableStateOf(c.destino) }
    var ativo by remember { mutableStateOf(c.ativo) }
    var teste by remember { mutableStateOf(c.ultimoTeste) }
    var confirmarExclusao by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (c.id == 0L) "Novo canal" else "Editar canal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(nome, { nome = it }, "Nome (ex.: Grupo Ofertas Tech)")
                Escolha("Tipo", TipoCanal.entries, tipo, { it.rotulo }, { tipo = it })
                if (tipo == TipoCanal.TELEGRAM) {
                    Campo(destino, { destino = it.trim() }, "@canal ou ID do grupo", ajuda = "O bot precisa ser administrador")
                    TextButton(enabled = destino.isNotBlank(), onClick = { escopo.launch {
                        teste = try { app.backend.testarTelegram(destino).let { "Testado: ${it.titulo ?: it.id}" } } catch (e: FalhaBackend) { "Erro: ${e.message}" }
                    } }) { Text("Testar destino") }
                    teste?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                } else {
                    Text("Este canal abre o compartilhamento do Android. Você escolhe o grupo e envia.", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Ativo", Modifier.weight(1f)); Switch(ativo, { ativo = it }) }
                if (c.id != 0L) TextButton(onClick = { confirmarExclusao = true }) { Text("Excluir canal", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nome.isBlank()) return@TextButton ctx.avisar("Informe o nome")
                if (tipo == TipoCanal.TELEGRAM && !Regex("""^(@[A-Za-z0-9_]{5,32}|-?\d{5,20})$""").matches(destino)) return@TextButton ctx.avisar("Destino do Telegram inválido")
                aoSalvar(c.copy(nome = nome.trim(), tipo = tipo, destino = if (tipo == TipoCanal.TELEGRAM) destino else "", ativo = ativo, ultimoTeste = teste), false)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Voltar") } },
    )
    if (confirmarExclusao) Confirmar("Excluir canal?", "Itens da fila deste canal passam para \"compartilhar\".", "Excluir", true, { aoSalvar(c, true) }, { confirmarExclusao = false })
}
