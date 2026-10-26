package bot.afiliados.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import bot.afiliados.AfiliadosApp
import bot.afiliados.data.FalhaBackend
import bot.afiliados.data.Repositorio
import bot.afiliados.data.TextoPromocional
import bot.afiliados.domain.Mensagem
import bot.afiliados.domain.TipoCanal
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private enum class Versao(val rotulo: String, val de: (TextoPromocional) -> String) {
    WHATSAPP("WhatsApp", { it.whatsapp }),
    CANAL("Canal de ofertas", { it.canal }),
    OBJETIVA("Objetiva", { it.objetiva }),
    CHAMATIVA("Chamativa", { it.chamativa }),
    COMPLETA("Completa", { listOf(it.titulo, it.descricao, it.cta).filter(String::isNotBlank).joinToString("\n\n") }),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OfertaTela(app: AfiliadosApp, nav: NavHostController, produtoInicial: Long?) {
    val repo = app.repo
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    var produtoId by rememberSaveable { mutableStateOf(produtoInicial) }
    val produtos by repo.dao.produtos().collectAsStateWithLifecycle(emptyList())
    val canais by repo.dao.canais().collectAsStateWithLifecycle(emptyList())
    val textos by remember(produtoId) { produtoId?.let { repo.dao.textos(it) } ?: flowOf(emptyList()) }.collectAsStateWithLifecycle(emptyList())
    val produto = produtos.firstOrNull { it.id == produtoId }

    var textoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var versao by rememberSaveable { mutableStateOf(Versao.WHATSAPP) }
    var texto by rememberSaveable { mutableStateOf("") }
    var editado by rememberSaveable { mutableStateOf(false) }
    var canalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var gerando by remember { mutableStateOf(false) }
    var avisos by remember { mutableStateOf<List<String>>(emptyList()) }
    var erro by remember { mutableStateOf<String?>(null) }

    val atual = textos.firstOrNull { it.id == textoId } ?: textos.firstOrNull()
    LaunchedEffect(atual?.id, versao) {
        if (atual != null && !editado) { texto = versao.de(atual); avisos = atual.avisos.lines().filter(String::isNotBlank) }
    }
    val canal = canais.firstOrNull { it.id == canalId }
    val nichos by repo.dao.nichos().collectAsStateWithLifecycle(emptyList())
    // Canal padrão do nicho, se houver.
    LaunchedEffect(produto?.id, nichos) {
        if (canalId == null) canalId = nichos.firstOrNull { it.id == produto?.nichoId }?.canalId
    }
    LaunchedEffect(canal?.tipo) {
        if (!editado) versao = if (canal?.tipo == TipoCanal.TELEGRAM) Versao.CANAL else Versao.WHATSAPP
    }

    fun gerar() {
        val p = produto ?: return
        gerando = true; erro = null
        escopo.launch {
            try {
                val (id, av) = repo.gerarTexto(p, if (canal?.tipo == TipoCanal.TELEGRAM) "telegram" else "whatsapp", atual)
                textoId = id; editado = false; avisos = av
            } catch (e: FalhaBackend) {
                erro = "IA indisponível: ${e.message}. Você pode escrever o texto à mão abaixo."
            } finally {
                gerando = false
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Criar oferta") }, navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
        })
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = espaco, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                if (produtos.isEmpty()) EstadoVazio("Sem produtos", "Cadastre um produto primeiro.") { Button(onClick = { nav.irPara(Rotas.novoProduto()) }) { Text("Novo produto") } }
                else Escolha("Produto", produtos, produto, { it.nome }, { produtoId = it.id; textoId = null; editado = false; texto = "" })
            }
            if (produto != null) {
                if (produto.linkAfiliado == null) item { Aviso("Este produto não tem link de afiliado. Adicione antes de publicar.", erro = true) }
                item {
                    Escolha("Canal", listOf(null) + canais.filter { it.ativo }, canal, { c -> c?.let { "${it.nome} · ${it.tipo.rotulo}" } ?: "Só compartilhar (escolho na hora)" }, { canalId = it?.id })
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = ::gerar, enabled = !gerando) { Text(if (textos.isEmpty()) "Gerar com IA" else "Gerar outra versão") }
                        if (gerando) CircularProgressIndicator()
                    }
                    Text("A IA usa só os dados salvos do produto. Versões anteriores ficam guardadas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                erro?.let { item { Aviso(it, erro = true) } }
                if (avisos.isNotEmpty()) item { Aviso("Confira antes de usar:\n• " + avisos.joinToString("\n• "), erro = true) }
                if (textos.size > 1) item {
                    Escolha("Versão salva", textos, atual, { "${Repositorio.dataHora(it.criadoEm)} · ${if (it.origem == "ia") "IA" else "manual"}" }, { textoId = it.id; editado = false })
                }
                if (atual != null) item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Versao.entries.forEach { v -> Chip(versao == v, { versao = v; editado = false }, label = { Text(v.rotulo) }) }
                    }
                }
                item { Campo(texto, { texto = it; editado = true }, "Texto da oferta (sem o link)", linhas = 6, ajuda = "O link de afiliado entra no final, sem alteração") }
                if (atual?.hashtags?.isNotBlank() == true) item { Text("Hashtags: ${atual.hashtags}", style = MaterialTheme.typography.bodySmall) }
                if (texto.isNotBlank() && produto.linkAfiliado != null) item {
                    Secao("Prévia") { Text(Mensagem.montar(texto, produto.linkAfiliado, atual?.hashtags?.split(' ').orEmpty())) }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = texto.isNotBlank(), onClick = { escopo.launch { textoId = repo.salvarTextoManual(produto.id, texto); editado = false; ctx.avisar("Texto salvo como nova versão") } }) { Text("Salvar como versão") }
                        Button(enabled = texto.isNotBlank() && produto.linkAfiliado != null, onClick = {
                            escopo.launch {
                                when (val r = repo.adicionarAFila(produto, texto, atual?.hashtags.orEmpty(), canalId)) {
                                    is Repositorio.ResultadoFila.Criado -> { nav.popBackStack(); nav.irPara(Rotas.item(r.id)) }
                                    is Repositorio.ResultadoFila.JaNaFila -> { ctx.avisar("Esse produto já está na fila deste canal"); nav.irPara(Rotas.item(r.item.id)) }
                                    is Repositorio.ResultadoFila.SemLink -> ctx.avisar(r.motivo)
                                }
                            }
                        }) { Text("Adicionar à fila") }
                    }
                }
            }
            item { Rodape() }
        }
    }
}
