package bot.afiliados.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import bot.afiliados.AfiliadosApp
import bot.afiliados.data.Repositorio
import bot.afiliados.domain.EstadoFila
import bot.afiliados.domain.Precos
import bot.afiliados.domain.SituacaoLink
import bot.afiliados.domain.SituacaoPreco
import bot.afiliados.domain.StatusComissao

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InicioTela(app: AfiliadosApp, nav: NavHostController) {
    val dao = app.repo.dao
    val produtos by dao.produtos().collectAsStateWithLifecycle(emptyList())
    val nichos by dao.nichos().collectAsStateWithLifecycle(emptyList())
    val porNicho by dao.contagemPorNicho().collectAsStateWithLifecycle(emptyList())
    val fila by dao.contagemFila().collectAsStateWithLifecycle(emptyList())
    val comTexto by dao.produtosComTexto().collectAsStateWithLifecycle(0)
    val somas by dao.somaComissoes().collectAsStateWithLifecycle(emptyList())
    val atividades by dao.atividades().collectAsStateWithLifecycle(emptyList())

    val n = fila.associate { it.estado to it.total }
    val pendentes = n.filterKeys { !it.final }.values.sum()
    val semLink = produtos.count { it.linkAfiliado == null || it.situacaoLink == SituacaoLink.INVALIDO }
    val desatualizados = produtos.count { app.repo.situacaoPreco(it).let { s -> s != SituacaoPreco.VERIFICADO && s != SituacaoPreco.VERIFICADO_MANUAL } }
    val soma = somas.associate { it.status to it.total }

    LazyColumn(contentPadding = espaco, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TopAppBar(title = { Text("Afiliados.bot", fontWeight = FontWeight.Bold) }) }
        if (!app.backend.configurado) item {
            Cartao(Modifier.fillMaxWidth().clickable { nav.irPara("ajustes") }) {
                Text("Backend não configurado. O catálogo funciona, mas IA, Shopee, Mercado Livre e Telegram ficam desligados. Toque para configurar.", Modifier.padding(16.dp))
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Atalho("Novo produto", Icons.Outlined.AddLink) { nav.irPara(Rotas.novoProduto()) }
                Atalho("Criar oferta", Icons.Outlined.AutoAwesome) { nav.irPara(Rotas.oferta()) }
                Atalho("Catálogo", Icons.Outlined.Inventory2) { nav.irPara("produtos") }
                Atalho("Fila", Icons.Outlined.Schedule) { nav.irPara("fila") }
                Atalho("Comissões", Icons.Outlined.Payments) { nav.irPara("comissoes") }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
                val m = Modifier.weight(1f)
                Numero("Produtos", produtos.size, m)
                Numero("Com texto pronto", comTexto, m)
                Numero("Publicações pendentes", pendentes, m)
                Numero("Publicadas (confirmadas)", n[EstadoFila.PUBLICADO_CONFIRMADO] ?: 0, m, "Telegram confirmou o envio")
                Numero("Publicadas (informadas)", n[EstadoFila.PUBLICADO_INFORMADO] ?: 0, m, "Você marcou como enviadas")
                Numero("Links a verificar", semLink, m, "Sem link de afiliado ou inválido")
                Numero("Preço a conferir", desatualizados, m, "Vencido, nunca verificado ou indisponível")
                Numero("Falhas / não confirmadas", (n[EstadoFila.FALHA] ?: 0) + (n[EstadoFila.ENVIO_NAO_CONFIRMADO] ?: 0), m)
            }
        }
        item {
            Secao("Comissões") {
                Linha("Estimadas", Precos.formatar(soma[StatusComissao.ESTIMADA.name] ?: 0.0))
                Linha("Registradas (não confirmadas)", Precos.formatar(soma[StatusComissao.REGISTRADA.name] ?: 0.0))
                Linha("Confirmadas pela loja", Precos.formatar(soma[StatusComissao.CONFIRMADA.name] ?: 0.0))
                Linha("Pagas", Precos.formatar(soma[StatusComissao.PAGA.name] ?: 0.0))
                Text("Valores que você registrou. Nenhum número aqui é calculado a partir de vendas presumidas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Secao("Produtos por nicho") {
                if (produtos.isEmpty()) Text("Nenhum produto ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                val mapa = porNicho.associate { it.nichoId to it.total }
                nichos.filter { (mapa[it.id] ?: 0) > 0 }.forEach { Linha("${it.icone} ${it.nome}", "${mapa[it.id]}") }
                mapa[null]?.let { Linha("Sem nicho", "$it") }
            }
        }
        item {
            Secao("Últimas atividades") {
                if (atividades.isEmpty()) Text("Nada por aqui ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                atividades.forEach {
                    Row {
                        Text(Repositorio.dataHora(it.quando), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(76.dp).padding(top = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(it.texto, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item { Rodape() }
    }
}

@Composable
private fun Atalho(nome: String, icone: ImageVector, aoClicar: () -> Unit) {
    FilledTonalButton(onClick = aoClicar) {
        Icon(icone, null, Modifier.padding(end = 6.dp))
        Text(nome)
    }
}

@Composable
private fun Numero(nome: String, valor: Int, modifier: Modifier, ajuda: String? = null) {
    Cartao(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text("$valor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(nome, style = MaterialTheme.typography.bodyMedium)
            ajuda?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun Linha(rotulo: String, valor: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(rotulo, Modifier.weight(1f))
        Text(valor, fontWeight = FontWeight.SemiBold)
    }
}
