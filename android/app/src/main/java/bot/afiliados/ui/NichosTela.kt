package bot.afiliados.ui

import androidx.compose.foundation.layout.FlowRow

import bot.afiliados.data.comCanais

import bot.afiliados.data.canais

import android.database.sqlite.SQLiteConstraintException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import bot.afiliados.AfiliadosApp
import bot.afiliados.data.Canal
import bot.afiliados.data.Nicho
import bot.afiliados.domain.Fila
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NichosTela(app: AfiliadosApp, nav: NavHostController) {
    val dao = app.repo.dao
    val nichos by dao.nichos().collectAsStateWithLifecycle(emptyList())
    val contagem by dao.contagemPorNicho().collectAsStateWithLifecycle(emptyList())
    val canais by dao.canais().collectAsStateWithLifecycle(emptyList())
    val produtos by dao.produtos().collectAsStateWithLifecycle(emptyList())
    val pendentesPorProduto by remember { dao.fila().map { l -> l.filter { it.estado in Fila.ativos }.groupingBy { it.produtoId }.eachCount() } }.collectAsStateWithLifecycle(emptyMap())
    var editando by remember { mutableStateOf<Nicho?>(null) }
    var excluindo by remember { mutableStateOf<Nicho?>(null) }
    val escopo = rememberCoroutineScope()
    val ctx = LocalContext.current
    val total = contagem.associate { it.nichoId to it.total }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Nichos") }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { editando = Nicho(nome = "", ordem = nichos.size) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Nicho") }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = espaco, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (nichos.isEmpty()) item { EstadoVazio("Sem nichos", "Crie um nicho para organizar seus produtos.") }
            items(nichos, key = Nicho::id) { n ->
                val pend = produtos.filter { it.nichoId == n.id }.sumOf { pendentesPorProduto[it.id] ?: 0 }
                Cartao(Modifier.fillMaxWidth().clickable { editando = n }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(n.icone, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.nome, style = MaterialTheme.typography.titleMedium)
                            Text("${total[n.id] ?: 0} produtos · $pend ofertas pendentes" + (n.canais.mapNotNull { id -> canais.firstOrNull { it.id == id }?.nome }.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = " · ") ?: ""), style = MaterialTheme.typography.bodySmall)
                            if (n.descricao.isNotBlank()) Text(n.descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Rodape() }
        }
    }

    editando?.let { n ->
        EditorNicho(n, canais, aoFechar = { editando = null }, aoExcluir = if (n.id != 0L) ({ excluindo = n; editando = null }) else null) { salvo ->
            escopo.launch {
                try {
                    if (salvo.id == 0L) dao.inserir(salvo) else dao.atualizar(salvo)
                    app.repo.registrar("nicho", "Salvou o nicho ${salvo.nome}")
                    editando = null
                } catch (_: SQLiteConstraintException) {
                    ctx.avisar("Já existe um nicho com esse nome")
                }
            }
        }
    }
    excluindo?.let { n ->
        Confirmar("Excluir nicho?", "\"${n.nome}\" será apagado. Os ${total[n.id] ?: 0} produtos dele ficam sem nicho (não são apagados).", "Excluir", destrutivo = true,
            aoConfirmar = { escopo.launch { dao.excluir(n); app.repo.registrar("nicho", "Excluiu o nicho ${n.nome}") } }, aoFechar = { excluindo = null })
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EditorNicho(n: Nicho, canais: List<Canal>, aoFechar: () -> Unit, aoExcluir: (() -> Unit)?, aoSalvar: (Nicho) -> Unit) {
    var nome by remember { mutableStateOf(n.nome) }
    var icone by remember { mutableStateOf(n.icone) }
    var descricao by remember { mutableStateOf(n.descricao) }
    var pref by remember { mutableStateOf(n.preferenciasTexto) }
    var escolhidos by remember { mutableStateOf(n.canais) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (n.id == 0L) "Novo nicho" else "Editar nicho") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(nome, { nome = it }, "Nome")
                Campo(icone, { icone = it.take(4) }, "Ícone (emoji)")
                Campo(descricao, { descricao = it }, "Descrição (opcional)")
                Campo(pref, { pref = it.take(400) }, "Preferências de texto", linhas = 2, ajuda = "Ex.: tom descontraído, sem gírias")
                Text("Canais do nicho (a oferta sai em todos os marcados)", style = MaterialTheme.typography.labelLarge)
                if (canais.isEmpty()) Text("Crie canais em Ajustes.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    canais.forEach { c ->
                        val marcado = c.id in escolhidos
                        Chip(marcado, { escolhidos = if (marcado) escolhidos - c.id else escolhidos + c.id }, label = { Text(c.nome) })
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = nome.isNotBlank(), onClick = { aoSalvar(n.copy(nome = nome.trim(), icone = icone.ifBlank { "🏷️" }, descricao = descricao.trim(), preferenciasTexto = pref.trim()).comCanais(escolhidos)) }) { Text("Salvar") } },
        dismissButton = {
            Row {
                aoExcluir?.let { TextButton(onClick = it) { Text("Excluir", color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = aoFechar) { Text("Voltar") }
            }
        },
    )
}
