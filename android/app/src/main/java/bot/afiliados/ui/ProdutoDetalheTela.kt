package bot.afiliados.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import bot.afiliados.AfiliadosApp
import bot.afiliados.data.Repositorio
import bot.afiliados.domain.Precos
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProdutoDetalheTela(app: AfiliadosApp, nav: NavHostController, id: Long) {
    val repo = app.repo
    val p by repo.dao.produto(id).collectAsStateWithLifecycle(null)
    val historico by repo.dao.historico(id).collectAsStateWithLifecycle(emptyList())
    val textos by repo.dao.textos(id).collectAsStateWithLifecycle(emptyList())
    val nichos by repo.dao.nichos().collectAsStateWithLifecycle(emptyList())
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    var verificando by remember { mutableStateOf(false) }
    var dialogo by remember { mutableStateOf<String?>(null) }
    var precoManual by remember { mutableStateOf("") }

    val produto = p ?: run {
        Scaffold(topBar = { TopAppBar(title = { Text("Produto") }) }) { EstadoVazio("Produto não encontrado", "Ele pode ter sido excluído.", Modifier.padding(it)) }
        return
    }
    val sit = repo.situacaoPreco(produto)
    val desconto = Precos.desconto(produto.preco, produto.precoAnterior, produto.precoAnteriorComprovado)

    Scaffold(topBar = {
        TopAppBar(title = { Text("Produto") }, navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
        }, actions = { IconButton(onClick = { nav.irPara(Rotas.editar(id)) }) { Icon(Icons.Outlined.Edit, "Editar") } })
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = espaco, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            produto.imagemUrl?.takeIf { it.startsWith("https://") }?.let { url ->
                item { AsyncImage(url, null, Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit) }
            }
            item {
                Text(produto.nome, style = MaterialTheme.typography.titleLarge)
                Text("${produto.marketplace.rotulo} · ${nichos.firstOrNull { it.id == produto.nichoId }?.let { "${it.icone} ${it.nome}" } ?: "Sem nicho"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Secao("Preço e comissão") {
                    Linha("Preço registrado", Precos.formatar(produto.preco))
                    if (produto.precoAnterior != null) Linha("Preço anterior" + if (produto.precoAnteriorComprovado) " (comprovado)" else " (não comprovado)", Precos.formatar(produto.precoAnterior))
                    desconto?.let { Linha("Desconto", "$it%") }
                    Linha("Situação", sit.rotulo)
                    produto.ultimaVerificacao?.let { Linha("Última verificação", Repositorio.dataHora(it) + " · " + (produto.fontePreco ?: "")) }
                    Linha("Comissão", produto.comissaoPercentual?.let { "$it% (${if (produto.comissaoOrigem == "shopee_api") "API Shopee" else "informada"})" } ?: "—")
                    Precos.comissaoEstimada(produto.preco, produto.comissaoPercentual)?.let { Linha("Comissão estimada por venda", Precos.formatar(it)) }
                    produto.urgenciaFato?.let { Linha("Prazo informado pela loja", it) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !verificando, onClick = {
                            verificando = true
                            escopo.launch {
                                val v = repo.verificarPreco(produto)
                                verificando = false
                                ctx.avisar(v.resultado.rotulo + if (v.detalhe.isNotBlank()) ": ${v.detalhe}" else "")
                            }
                        }) { Text(if (verificando) "Verificando…" else "Verificar preço") }
                        OutlinedButton(onClick = { precoManual = produto.preco?.let { Precos.formatar(it).removePrefix("R$ ") }.orEmpty(); dialogo = "preco" }) { Text("Confirmar à mão") }
                    }
                }
            }
            item {
                Secao("Links") {
                    Text("Original: ${produto.linkOriginal}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        produto.linkAfiliado?.let { "Afiliado: $it" } ?: "Sem link de afiliado. Edite o produto para adicionar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (produto.linkAfiliado == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        produto.linkAfiliado?.let { l -> OutlinedButton(onClick = { ctx.copiar("Link", l) }) { Text("Copiar link") } }
                        OutlinedButton(onClick = { ctx.abrirLink(produto.linkAfiliado ?: produto.linkOriginal) }) { Text("Abrir na loja") }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { nav.irPara(Rotas.oferta(id)) }) { Text("Criar oferta / texto") }
                    OutlinedButton(onClick = { dialogo = "duplicar" }) { Text("Duplicar") }
                    OutlinedButton(onClick = { escopo.launch { repo.salvarEdicao(produto.copy(arquivado = !produto.arquivado)) } }) { Text(if (produto.arquivado) "Desarquivar" else "Arquivar") }
                    OutlinedButton(onClick = { escopo.launch { dialogo = if (repo.temHistorico(produto)) "excluir_hist" else "excluir" } }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                }
            }
            if (produto.caracteristicas.isNotBlank() || produto.observacoes.isNotBlank()) item {
                Secao("Anotações") {
                    if (produto.caracteristicas.isNotBlank()) Text(produto.caracteristicas)
                    if (produto.observacoes.isNotBlank()) Text("Obs.: ${produto.observacoes}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Secao("Textos salvos (${textos.size})") {
                    if (textos.isEmpty()) Text("Nenhum texto ainda. Toque em Criar oferta.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    textos.take(5).forEach { t ->
                        Text("${Repositorio.dataHora(t.criadoEm)} · ${if (t.origem == "ia") "IA" else "manual"}${if (t.avisos.isNotBlank()) " · com avisos" else ""}", style = MaterialTheme.typography.labelSmall)
                        Text(t.whatsapp, maxLines = 3, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                    }
                }
            }
            item {
                Secao("Histórico de preços") {
                    if (historico.isEmpty()) Text("Sem verificações registradas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    historico.take(20).forEach { v ->
                        Linha("${Repositorio.dataHora(v.consultadoEm)} · ${v.fonte}", v.preco?.let { Precos.formatar(it) + (v.diferenca?.takeIf { d -> d != 0.0 }?.let { d -> " (${if (d > 0) "+" else ""}${Precos.formatar(d).removePrefix("R$ ")})" } ?: "") } ?: v.resultado.rotulo)
                    }
                }
            }
            item { Rodape() }
        }
    }

    when (dialogo) {
        "duplicar" -> Confirmar("Duplicar produto?", "Vai criar uma cópia separada de \"${produto.nome}\". Use só se for de propósito (outra variação, outro link).", "Duplicar",
            aoConfirmar = { escopo.launch { nav.irPara(Rotas.produto(repo.duplicar(produto))) } }, aoFechar = { dialogo = null })
        "excluir" -> Confirmar("Excluir produto?", "\"${produto.nome}\" será apagado.", "Excluir", destrutivo = true,
            aoConfirmar = { escopo.launch { repo.excluir(produto); nav.popBackStack() } }, aoFechar = { dialogo = null })
        "excluir_hist" -> Confirmar("Excluir com histórico?", "Este produto tem histórico de preços ou itens na fila. Tudo isso será apagado junto. Se quiser só tirar da lista, use Arquivar.", "Excluir tudo", destrutivo = true,
            aoConfirmar = { escopo.launch { repo.excluir(produto); nav.popBackStack() } }, aoFechar = { dialogo = null })
        "preco" -> AlertDialog(
            onDismissRequest = { dialogo = null },
            title = { Text("Confirmar preço à mão") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("Abra a loja, confira o preço e digite. Fica registrado como \"confirmado por você\".")
                    Campo(precoManual, { precoManual = it }, "Preço que você viu (R$)", numero = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = Precos.ler(precoManual)
                    if (v == null || v <= 0) ctx.avisar("Preço inválido") else { escopo.launch { repo.confirmarPrecoManual(produto, v) }; dialogo = null }
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { dialogo = null }) { Text("Voltar") } },
        )
    }
}
