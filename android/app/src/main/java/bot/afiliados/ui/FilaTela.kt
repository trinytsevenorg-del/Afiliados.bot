package bot.afiliados.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.work.ExistingWorkPolicy
import bot.afiliados.AfiliadosApp
import bot.afiliados.data.ItemFila
import bot.afiliados.data.Repositorio
import bot.afiliados.data.fotos
import bot.afiliados.domain.EstadoFila
import bot.afiliados.domain.Fila
import bot.afiliados.domain.Mensagem
import bot.afiliados.domain.Precos
import bot.afiliados.domain.TipoCanal
import bot.afiliados.work.PublicarWorker
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilaTela(app: AfiliadosApp, nav: NavHostController) {
    val dao = app.repo.dao
    val fila by dao.fila().collectAsStateWithLifecycle(emptyList())
    val produtos by dao.produtos().collectAsStateWithLifecycle(emptyList())
    val canais by dao.canais().collectAsStateWithLifecycle(emptyList())
    var verConcluidos by rememberSaveable { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()
    val nomes = produtos.associate { it.id to it.nome }
    val lista = fila.filter { verConcluidos || !it.estado.final }
    val ativos = lista.filter { !it.estado.final }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Fila de publicação") }, actions = {
            Chip(verConcluidos, { verConcluidos = !verConcluidos }, label = { Text("Ver concluídos") }, modifier = Modifier.padding(end = 8.dp))
        })
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = espaco, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val prontosTelegram = fila.count { it.estado == EstadoFila.PRONTO && canais.firstOrNull { c -> c.id == it.canalId }?.tipo == TipoCanal.TELEGRAM }
            if (prontosTelegram > 0) item {
                val ctx = LocalContext.current
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    escopo.launch {
                        val itens = app.repo.agendarEmSequencia(app.repo.prontosAutomaticos(), app.prefs.intervaloMinutos)
                        itens.forEach { PublicarWorker.agendar(ctx, it, true, ExistingWorkPolicy.REPLACE) }
                        ctx.avisar("${itens.size} ofertas agendadas: uma a cada ${app.prefs.intervaloMinutos} min")
                    }
                }) { Text("🚀 Publicar todos no Telegram ($prontosTelegram)") }
                Text("Começa em 1 minuto e segue um a cada ${app.prefs.intervaloMinutos} min (mude em Ajustes).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (lista.isEmpty()) item {
                EstadoVazio("Fila vazia", "Crie uma oferta a partir de um produto e adicione à fila.") { Button(onClick = { nav.irPara(Rotas.oferta()) }) { Text("Criar oferta") } }
            }
            itemsIndexed(lista, key = { _, i -> i.id }) { idx, i ->
                Cartao(Modifier.fillMaxWidth().clickable { nav.irPara(Rotas.item(i.id)) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(nomes[i.produtoId] ?: "Produto", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(i.texto, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            Text(
                                (canais.firstOrNull { it.id == i.canalId }?.nome ?: "Compartilhar") + (i.planejadoPara?.let { " · " + Repositorio.dataHora(it) } ?: ""),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Selo(i.estado.rotulo, corFila(i.estado))
                        }
                        if (!i.estado.final) Column {
                            val pos = ativos.indexOf(i)
                            IconButton(enabled = pos > 0, onClick = { escopo.launch { trocar(app, i, ativos[pos - 1]) } }) { Icon(Icons.Outlined.KeyboardArrowUp, "Subir") }
                            IconButton(enabled = pos in 0 until ativos.lastIndex, onClick = { escopo.launch { trocar(app, i, ativos[pos + 1]) } }) { Icon(Icons.Outlined.KeyboardArrowDown, "Descer") }
                        }
                    }
                }
            }
            item { Rodape() }
        }
    }
}

private suspend fun trocar(app: AfiliadosApp, a: ItemFila, b: ItemFila) {
    val dao = app.repo.dao
    val oa = if (a.ordem == b.ordem) a.ordem + 1 else a.ordem
    dao.atualizar(a.copy(ordem = b.ordem))
    dao.atualizar(b.copy(ordem = oa))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemFilaTela(app: AfiliadosApp, nav: NavHostController, id: Long) {
    val repo = app.repo
    val dao = repo.dao
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val itemOuNulo by dao.item(id).collectAsStateWithLifecycle(null)
    val tentativas by dao.tentativas(id).collectAsStateWithLifecycle(emptyList())
    val canais by dao.canais().collectAsStateWithLifecycle(emptyList())
    val item = itemOuNulo ?: run {
        Scaffold(topBar = { TopAppBar(title = { Text("Item da fila") }) }) { EstadoVazio("Item não encontrado", "Ele pode ter sido removido.", Modifier.padding(it)) }
        return
    }
    val produto by dao.produto(item.produtoId).collectAsStateWithLifecycle(null)
    val canal = canais.firstOrNull { it.id == item.canalId }
    var texto by remember(item.id) { mutableStateOf(item.texto) }
    LaunchedEffect(item.texto) { texto = item.texto }
    var dialogo by remember { mutableStateOf<String?>(null) }
    var verificando by remember { mutableStateOf(false) }

    val sit = produto?.let { repo.situacaoPreco(it) }
    val bloqueios = if (sit != null) Fila.bloqueios(texto, item.link, sit, precoConfirmadoAgora = false) else listOf("Produto não encontrado")
    val mensagem = Mensagem.montar(texto, item.link, item.hashtags.split(' ').filter { it.isNotBlank() })
    val automatico = canal?.tipo == TipoCanal.TELEGRAM
    val editavel = item.estado in setOf(EstadoFila.RASCUNHO, EstadoFila.AGUARDANDO_REVISAO, EstadoFila.AGUARDANDO_VERIFICACAO_PRECO, EstadoFila.PRONTO, EstadoFila.FALHA)

    fun mudar(novo: EstadoFila, msg: String? = null, depois: suspend (ItemFila) -> Unit = {}) = escopo.launch {
        try {
            val atual = dao.itemAgora(id) ?: return@launch
            val salvo = if (atual.texto != texto) atual.copy(texto = texto).also { dao.atualizar(it) } else atual
            depois(repo.mudar(salvo, novo, msg ?: salvo.ultimoResultado))
        } catch (e: IllegalArgumentException) { ctx.avisar(e.message ?: "Mudança inválida") }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Revisar oferta") }, navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
        })
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = espaco, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(produto?.nome ?: "Produto", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Selo(item.estado.rotulo, corFila(item.estado))
                    sit?.let { Selo(it.rotulo, corPreco(it)) }
                }
                Text("Canal: ${canal?.let { "${it.nome} · ${it.tipo.rotulo}" } ?: "Compartilhar (escolho na hora)"}", style = MaterialTheme.typography.bodyMedium)
                item.planejadoPara?.let { Text("Planejado para ${Repositorio.dataHora(it)}" + if (!automatico) " (você recebe um lembrete)" else "", style = MaterialTheme.typography.bodyMedium) }
                item.ultimoResultado?.let { Text("Último resultado: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (bloqueios.isNotEmpty() && !item.estado.final) item {
                Aviso("Antes de publicar:\n• " + bloqueios.joinToString("\n• "), erro = true)
                if (produto != null && sit != null && !Precos.podePublicarSemAviso(sit)) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !verificando, onClick = {
                        verificando = true
                        escopo.launch { val v = repo.verificarPreco(produto!!); verificando = false; ctx.avisar(v.resultado.rotulo) }
                    }) { Text(if (verificando) "Verificando…" else "Verificar preço agora") }
                    OutlinedButton(onClick = { nav.irPara(Rotas.produto(item.produtoId)) }) { Text("Confirmar à mão") }
                }
            }
            if (item.estado == EstadoFila.ENVIO_NAO_CONFIRMADO) item {
                Aviso("Não sabemos se a mensagem chegou. Olhe o canal: se apareceu, marque como publicada. Se não, tente de novo — o backend não posta duas vezes a mesma oferta.")
            }
            item {
                if (editavel) Campo(texto, { texto = it }, "Texto (sem o link)", linhas = 5)
                Secao("Como vai sair") { Text(mensagem) }
                Text("Preço registrado: ${Precos.formatar(produto?.preco)}", style = MaterialTheme.typography.bodySmall)
            }
            if (!item.estado.final) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (item.estado) {
                        EstadoFila.RASCUNHO, EstadoFila.AGUARDANDO_REVISAO, EstadoFila.AGUARDANDO_VERIFICACAO_PRECO, EstadoFila.FALHA -> {
                            Button(onClick = {
                                if (bloqueios.isEmpty()) mudar(EstadoFila.PRONTO, "Revisado")
                                else if (item.estado != EstadoFila.AGUARDANDO_VERIFICACAO_PRECO && item.estado != EstadoFila.FALHA && bloqueios.any { it.startsWith("Preço") }) mudar(EstadoFila.AGUARDANDO_VERIFICACAO_PRECO, bloqueios.first())
                                else ctx.avisar(bloqueios.first())
                            }) { Text("Revisado, deixar pronto") }
                        }
                        EstadoFila.PRONTO, EstadoFila.AGENDADO -> {
                            if (automatico) Button(enabled = bloqueios.isEmpty(), onClick = {
                                escopo.launch {
                                    val atual = dao.itemAgora(id) ?: return@launch
                                    val pronto = if (atual.estado == EstadoFila.AGENDADO) atual else repo.mudar(atual.copy(texto = texto), EstadoFila.AGENDADO, "Enviar agora")
                                    val agora = pronto.copy(planejadoPara = System.currentTimeMillis()).also { dao.atualizar(it) }
                                    PublicarWorker.agendar(ctx, agora, true, ExistingWorkPolicy.REPLACE)
                                    ctx.avisar("Enviando. O resultado aparece aqui.")
                                }
                            }) { Text("Publicar agora") }
                            else Button(enabled = bloqueios.isEmpty(), onClick = {
                                ctx.compartilhar(mensagem, if (canal?.tipo == TipoCanal.WHATSAPP_COMPARTILHAR) "com.whatsapp" else null, produto?.fotos.orEmpty())
                                dialogo = "confirmar_envio"
                            }) { Text(if (canal?.tipo == TipoCanal.WHATSAPP_COMPARTILHAR) "Abrir no WhatsApp" else "Compartilhar") }
                            OutlinedButton(onClick = { dialogo = "agendar" }) { Text(if (item.estado == EstadoFila.AGENDADO) "Mudar horário" else "Agendar") }
                            if (item.estado == EstadoFila.AGENDADO) OutlinedButton(onClick = { PublicarWorker.cancelar(ctx, id); mudar(EstadoFila.PRONTO, "Agendamento cancelado") }) { Text("Tirar da agenda") }
                        }
                        EstadoFila.ENVIO_NAO_CONFIRMADO -> {
                            Button(onClick = { escopo.launch { repo.publicarAutomatico(id) } }) { Text("Tentar de novo") }
                            OutlinedButton(onClick = { mudar(EstadoFila.PUBLICADO_INFORMADO, "Você conferiu no canal") }) { Text("Apareceu no canal") }
                        }
                        else -> {}
                    }
                    if (Fila.pode(item.estado, EstadoFila.CANCELADO)) OutlinedButton(onClick = { dialogo = "cancelar" }) { Text("Cancelar", color = MaterialTheme.colorScheme.error) }
                    if (item.estado != EstadoFila.EM_PROCESSAMENTO) TextButton(onClick = { dialogo = "remover" }) { Text("Remover da fila", color = MaterialTheme.colorScheme.error) }
                }
            }
            if (canais.isNotEmpty() && editavel) item {
                Escolha("Trocar canal", listOf(null) + canais, canal, { it?.nome ?: "Compartilhar" }, { c -> escopo.launch { dao.itemAgora(id)?.let { dao.atualizar(it.copy(canalId = c?.id)) } } })
            }
            item {
                Secao("Tentativas (${tentativas.size})") {
                    if (tentativas.isEmpty()) Text("Nenhuma tentativa automática.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    tentativas.forEach { t -> Text("${Repositorio.dataHora(t.inicio)} · ${t.resultado?.rotulo ?: "em andamento"} · ${t.detalhe}", style = MaterialTheme.typography.bodySmall) }
                }
            }
            item { Rodape() }
        }
    }

    when (dialogo) {
        "confirmar_envio" -> AlertDialog(
            onDismissRequest = { dialogo = null },
            title = { Text("Você publicou?") },
            text = { Text("Abrir o WhatsApp não confirma o envio. Marque só se a mensagem foi enviada de fato.") },
            confirmButton = { TextButton(onClick = { PublicarWorker.cancelar(ctx, id); mudar(EstadoFila.PUBLICADO_INFORMADO, "Você informou que enviou"); dialogo = null }) { Text("Sim, enviei") } },
            dismissButton = { TextButton(onClick = { dialogo = null }) { Text("Ainda não") } },
        )
        "cancelar" -> Confirmar("Cancelar oferta?", "Ela sai da fila de publicação (o histórico fica).", "Cancelar oferta", destrutivo = true,
            aoConfirmar = { PublicarWorker.cancelar(ctx, id); mudar(EstadoFila.CANCELADO, "Cancelado por você") }, aoFechar = { dialogo = null })
        "remover" -> Confirmar("Remover da fila?", "O item e as tentativas serão apagados.", "Remover", destrutivo = true,
            aoConfirmar = { PublicarWorker.cancelar(ctx, id); escopo.launch { dao.itemAgora(id)?.let { dao.excluir(it) }; nav.popBackStack() } }, aoFechar = { dialogo = null })
        "agendar" -> SeletorHorario(item.planejadoPara, aoFechar = { dialogo = null }) { quando ->
            dialogo = null
            if (quando < System.currentTimeMillis() + 60_000) { ctx.avisar("Escolha um horário no futuro"); return@SeletorHorario }
            escopo.launch {
                val atual = dao.itemAgora(id) ?: return@launch
                val base = atual.copy(texto = texto, planejadoPara = quando).also { dao.atualizar(it) }
                val ag = if (base.estado == EstadoFila.AGENDADO) base else repo.mudar(base, EstadoFila.AGENDADO, "Agendado")
                PublicarWorker.agendar(ctx, ag, automatico, ExistingWorkPolicy.REPLACE)
                repo.registrar("fila", "Agendou ${produto?.nome} para ${Repositorio.dataHora(quando)}")
                ctx.avisar(if (automatico) "Agendado. O Android pode atrasar alguns minutos." else "Agendado. Você recebe um lembrete para enviar.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorHorario(inicial: Long?, aoFechar: () -> Unit, aoEscolher: (Long) -> Unit) {
    val zona = ZoneId.systemDefault()
    val base = Instant.ofEpochMilli(inicial ?: (System.currentTimeMillis() + 3_600_000)).atZone(zona)
    var data by remember { mutableStateOf<LocalDate?>(null) }
    val dp = rememberDatePickerState(initialSelectedDateMillis = base.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    val tp = rememberTimePickerState(base.hour, base.minute, is24Hour = true)
    if (data == null) {
        DatePickerDialog(onDismissRequest = aoFechar, confirmButton = {
            TextButton(onClick = { dp.selectedDateMillis?.let { data = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } }) { Text("Próximo") }
        }, dismissButton = { TextButton(onClick = aoFechar) { Text("Voltar") } }) { DatePicker(dp) }
    } else {
        AlertDialog(onDismissRequest = aoFechar, title = { Text("Horário") }, text = { TimePicker(tp) },
            confirmButton = { TextButton(onClick = { aoEscolher(data!!.atTime(LocalTime.of(tp.hour, tp.minute)).atZone(zona).toInstant().toEpochMilli()) }) { Text("Agendar") } },
            dismissButton = { TextButton(onClick = aoFechar) { Text("Voltar") } })
    }
}
