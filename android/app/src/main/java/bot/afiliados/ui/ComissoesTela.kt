package bot.afiliados.ui

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import bot.afiliados.AfiliadosApp
import bot.afiliados.data.Comissao
import bot.afiliados.data.Produto
import bot.afiliados.data.Repositorio
import bot.afiliados.domain.Marketplace
import bot.afiliados.domain.Precos
import bot.afiliados.domain.StatusComissao
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComissoesTela(app: AfiliadosApp, nav: NavHostController) {
    val dao = app.repo.dao
    val lista by dao.comissoes().collectAsStateWithLifecycle(emptyList())
    val somas by dao.somaComissoes().collectAsStateWithLifecycle(emptyList())
    val produtos by dao.produtos().collectAsStateWithLifecycle(emptyList())
    var editando by remember { mutableStateOf<Comissao?>(null) }
    var excluindo by remember { mutableStateOf<Comissao?>(null) }
    val escopo = rememberCoroutineScope()
    val s = somas.associateBy { it.status }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Comissões") }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { editando = Comissao(marketplace = Marketplace.SHOPEE, valor = 0.0, status = StatusComissao.REGISTRADA) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Registrar") }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = espaco, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Secao("Resumo") {
                    StatusComissao.entries.forEach { st -> Linha("${st.rotulo} (${s[st.name]?.quantidade ?: 0})", Precos.formatar(s[st.name]?.total ?: 0.0)) }
                    Text("Comissão estimada não é dinheiro. Só \"Paga\" significa valor recebido.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Shopee e Mercado Livre não estão enviando relatórios de vendas para o app: os registros são feitos por você com base nos painéis de afiliado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (lista.isEmpty()) item { EstadoVazio("Nenhuma comissão registrada", "Quando o painel da loja mostrar uma venda, registre aqui.") }
            items(lista, key = Comissao::id) { c ->
                Cartao(Modifier.fillMaxWidth().clickable { editando = c }) {
                    Row(Modifier.padding(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(produtos.firstOrNull { it.id == c.produtoId }?.nome ?: "Sem produto associado", style = MaterialTheme.typography.titleSmall)
                            Text("${c.marketplace.rotulo} · ${c.status.rotulo} · ${c.origem}" + (c.dataVenda?.let { " · venda ${Repositorio.dataHora(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(Precos.formatar(c.valor), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            item { Rodape() }
        }
    }

    editando?.let { c ->
        EditorComissao(c, produtos, aoFechar = { editando = null }, aoExcluir = if (c.id != 0L) ({ excluindo = c; editando = null }) else null) { nova ->
            escopo.launch {
                if (nova.id == 0L) dao.inserir(nova) else dao.atualizar(nova)
                app.repo.registrar("comissao", "Registrou comissão ${nova.status.rotulo.lowercase()}: ${Precos.formatar(nova.valor)}")
                editando = null
            }
        }
    }
    excluindo?.let { c -> Confirmar("Excluir registro?", "A comissão de ${Precos.formatar(c.valor)} será apagada.", "Excluir", true, { escopo.launch { dao.excluir(c) } }, { excluindo = null }) }
}

@Composable
private fun EditorComissao(c: Comissao, produtos: List<Produto>, aoFechar: () -> Unit, aoExcluir: (() -> Unit)?, aoSalvar: (Comissao) -> Unit) {
    val ctx = LocalContext.current
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    var valor by remember { mutableStateOf(if (c.valor > 0) Precos.formatar(c.valor).removePrefix("R$ ") else "") }
    var pct by remember { mutableStateOf(c.percentual?.toString().orEmpty()) }
    var status by remember { mutableStateOf(c.status) }
    var loja by remember { mutableStateOf(c.marketplace) }
    var produtoId by remember { mutableStateOf(c.produtoId) }
    var data by remember { mutableStateOf(c.dataVenda?.let { fmt.format(java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }.orEmpty()) }
    var origem by remember { mutableStateOf(c.origem) }
    var obs by remember { mutableStateOf(c.observacao) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (c.id == 0L) "Registrar comissão" else "Editar comissão") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Campo(valor, { valor = it }, "Valor (R$)", numero = true) }
                item { Escolha("Status", StatusComissao.entries, status, { it.rotulo }, { status = it }) }
                item { Escolha("Loja", Marketplace.entries, loja, { it.rotulo }, { loja = it }) }
                item { Escolha("Produto", listOf(null) + produtos, produtos.firstOrNull { it.id == produtoId }, { it?.nome ?: "Nenhum" }, { produtoId = it?.id; it?.let { p -> loja = p.marketplace } }) }
                item { Campo(pct, { pct = it }, "Comissão (%)", numero = true) }
                item { Campo(data, { data = it }, "Data da venda (dd/mm/aaaa)") }
                item { Campo(origem, { origem = it }, "Origem da informação", ajuda = "Ex.: painel de afiliados da Shopee") }
                item { Campo(obs, { obs = it }, "Observação") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = Precos.ler(valor)
                val d = data.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.trim(), fmt) }.getOrNull() }
                when {
                    v == null || v <= 0 -> ctx.avisar("Informe um valor maior que zero")
                    data.isNotBlank() && d == null -> ctx.avisar("Data inválida")
                    else -> aoSalvar(c.copy(valor = v, percentual = Precos.ler(pct), status = status, marketplace = loja, produtoId = produtoId,
                        dataVenda = d?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(), origem = origem.ifBlank { "manual" }, observacao = obs))
                }
            }) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                aoExcluir?.let { TextButton(onClick = it) { Text("Excluir", color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = aoFechar) { Text("Voltar") }
            }
        },
    )
}
