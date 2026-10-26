package bot.afiliados.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import bot.afiliados.AfiliadosApp
import bot.afiliados.data.Nicho
import bot.afiliados.data.Produto
import bot.afiliados.data.fotos
import bot.afiliados.domain.Marketplace
import bot.afiliados.domain.Precos
import coil.compose.AsyncImage

private enum class Filtro(val rotulo: String) { TODOS("Todos"), SEM_LINK("Sem link afiliado"), PRECO("Preço a conferir"), ARQUIVADOS("Arquivados") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProdutosTela(app: AfiliadosApp, nav: NavHostController) {
    var busca by rememberSaveable { mutableStateOf("") }
    var grade by rememberSaveable { mutableStateOf(false) }
    var filtro by rememberSaveable { mutableStateOf(Filtro.TODOS) }
    var nichoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var loja by rememberSaveable { mutableStateOf<Marketplace?>(null) }
    val ativos by app.repo.dao.produtos(false).collectAsStateWithLifecycle(emptyList())
    val arquivados by app.repo.dao.produtos(true).collectAsStateWithLifecycle(emptyList())
    val nichos by app.repo.dao.nichos().collectAsStateWithLifecycle(emptyList())
    val nomeNicho = nichos.associate { it.id to "${it.icone} ${it.nome}" }

    val lista = (if (filtro == Filtro.ARQUIVADOS) arquivados else ativos).filter { p ->
        (busca.isBlank() || p.nome.contains(busca.trim(), ignoreCase = true)) &&
            (nichoId == null || p.nichoId == nichoId) &&
            (loja == null || p.marketplace == loja) &&
            when (filtro) {
                Filtro.SEM_LINK -> p.linkAfiliado == null
                Filtro.PRECO -> !Precos.podePublicarSemAviso(app.repo.situacaoPreco(p))
                else -> true
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Produtos") }, actions = {
                IconButton(onClick = { grade = !grade }) { Icon(if (grade) Icons.Outlined.ViewList else Icons.Outlined.GridView, "Mudar visualização") }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.irPara(Rotas.novoProduto()) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Produto") })
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = if (grade) GridCells.Adaptive(160.dp) else GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = espaco,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(busca, { busca = it }, Modifier.fillMaxWidth(), placeholder = { Text("Buscar por nome") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(Filtro.entries) { f -> Chip(filtro == f, { filtro = f }, label = { Text(f.rotulo) }) }
                        items(Marketplace.entries) { m -> Chip(loja == m, { loja = if (loja == m) null else m }, label = { Text(m.rotulo) }) }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(nichos, key = Nicho::id) { n -> Chip(nichoId == n.id, { nichoId = if (nichoId == n.id) null else n.id }, label = { Text("${n.icone} ${n.nome}") }) }
                    }
                }
            }
            if (lista.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                EstadoVazio(
                    if (ativos.isEmpty() && filtro != Filtro.ARQUIVADOS) "Catálogo vazio" else "Nada encontrado",
                    if (ativos.isEmpty()) "Cole o link de um produto da Shopee ou do Mercado Livre para começar." else "Mude a busca ou os filtros.",
                )
            }
            items(lista, key = Produto::id) { p ->
                if (grade) CartaoGrade(app, p, nomeNicho[p.nichoId]) { nav.irPara(Rotas.produto(p.id)) }
                else CartaoLista(app, p, nomeNicho[p.nichoId]) { nav.irPara(Rotas.produto(p.id)) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Rodape() }
        }
    }
}

@Composable
private fun Imagem(p: Produto, modifier: Modifier) {
    val modelo: Any? = p.fotos.firstOrNull() ?: p.imagemUrl?.takeIf { it.startsWith("https://") }
    if (modelo != null) {
        AsyncImage(model = modelo, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.clip(RoundedCornerShape(8.dp)))
    } else {
        Cartao(modifier) { Text("Sem imagem", Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun Selos(app: AfiliadosApp, p: Produto) {
    val sit = app.repo.situacaoPreco(p)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Selo(sit.rotulo, corPreco(sit))
        if (p.linkAfiliado == null) Selo("Sem link afiliado", MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun CartaoLista(app: AfiliadosApp, p: Produto, nicho: String?, aoClicar: () -> Unit) {
    Cartao(Modifier.fillMaxWidth().clickable(onClick = aoClicar)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Imagem(p, Modifier.size(64.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.nome, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text("${p.marketplace.rotulo} · ${nicho ?: "Sem nicho"} · ${Precos.formatar(p.preco)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Selos(app, p)
            }
        }
    }
}

@Composable
private fun CartaoGrade(app: AfiliadosApp, p: Produto, nicho: String?, aoClicar: () -> Unit) {
    Cartao(Modifier.fillMaxWidth().clickable(onClick = aoClicar)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Imagem(p, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(p.nome, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(Precos.formatar(p.preco), style = MaterialTheme.typography.bodyMedium)
            Text("${p.marketplace.rotulo} · ${nicho ?: "Sem nicho"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Selos(app, p)
        }
    }
}
