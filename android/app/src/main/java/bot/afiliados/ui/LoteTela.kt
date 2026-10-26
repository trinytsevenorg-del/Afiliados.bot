package bot.afiliados.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import bot.afiliados.AfiliadosApp
import bot.afiliados.data.FalhaBackend
import bot.afiliados.data.Produto
import bot.afiliados.data.Repositorio
import bot.afiliados.data.fotos
import bot.afiliados.domain.Links
import bot.afiliados.domain.Precos
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** Lê um print por vez. O progresso fica no ViewModel; os produtos já vão para o banco. */
class LoteVM(private val repo: Repositorio) : ViewModel() {
    var total by mutableStateOf(0)
    var feitos by mutableStateOf(0)
    var erros by mutableStateOf<List<String>>(emptyList())
    val rodando get() = feitos < total

    fun processar(ctx: android.content.Context, uris: List<android.net.Uri>) {
        if (uris.isEmpty() || rodando) return
        total = uris.size; feitos = 0; erros = emptyList()
        viewModelScope.launch {
            uris.forEachIndexed { i, u ->
                try {
                    repo.produtoDoPrint(ctx, u)
                } catch (e: FalhaBackend) {
                    erros = erros + "Print ${i + 1}: ${e.message}"
                } catch (e: Exception) {
                    erros = erros + "Print ${i + 1}: ${e.message ?: "falhou"}"
                }
                feitos = i + 1
            }
            repo.registrar("lote", "Lote: ${total - erros.size} de $total prints viraram produtos")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoteTela(app: AfiliadosApp, nav: NavHostController) {
    val vm: LoteVM = viewModel { LoteVM(app.repo) }
    val ctx = LocalContext.current
    val escopo = androidx.compose.runtime.rememberCoroutineScope()
    val semLink by app.repo.semLink().collectAsStateWithLifecycle(emptyList())
    val nichos by app.repo.dao.nichos().collectAsStateWithLifecycle(emptyList())
    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { vm.processar(ctx, it) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Lote de prints") }, navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
        })
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = espaco, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Secao("1 · Mandar os prints") {
                    Text("Um print por produto (com GANHOS e o preço aparecendo). Até 30 de uma vez. A IA lê, recorta a foto, escolhe o nicho e escreve o título.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(enabled = !vm.rodando, onClick = { escolher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text("Escolher prints")
                    }
                    if (vm.total > 0) {
                        LinearProgressIndicator(progress = { vm.feitos.toFloat() / vm.total }, modifier = Modifier.fillMaxWidth())
                        Text(if (vm.rodando) "Lendo ${vm.feitos + 1} de ${vm.total}… pode deixar o app aberto." else "Pronto: ${vm.total - vm.erros.size} de ${vm.total} viraram produtos.")
                    }
                    if (vm.erros.isNotEmpty()) Aviso(vm.erros.joinToString("\n"), erro = true)
                }
            }
            item {
                Secao("2 · Colar os links (${semLink.size})") {
                    Text("No app da loja, abra o produto → GANHOS → copiar link. Volte e toque em Colar no produto certo. A oferta já vai pronta para a fila, no canal do nicho.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (semLink.isEmpty()) Text("Nenhum produto esperando link.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(semLink, key = Produto::id) { p ->
                Cartao(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val foto = p.fotos.firstOrNull()
                        if (foto != null) AsyncImage(foto, null, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f)) {
                            Text(p.nome, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text("${Precos.formatar(p.preco)} · ${nichos.firstOrNull { it.id == p.nichoId }?.let { "${it.icone} ${it.nome}" } ?: "Sem nicho"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val texto = ctx.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                            val link = Links.extrairDeTexto(texto)
                            if (link == null) ctx.avisar("Não achei um link copiado")
                            else escopo.launch {
                                when (val r = app.repo.liberarComLink(p, link)) {
                                    is Repositorio.ResultadoFila.Criado -> ctx.avisar("Pronto na fila")
                                    is Repositorio.ResultadoFila.JaNaFila -> ctx.avisar("Já estava na fila")
                                    is Repositorio.ResultadoFila.SemLink -> ctx.avisar(r.motivo)
                                }
                            }
                        }) { Text("📋 Colar link") }
                        OutlinedButton(onClick = { nav.irPara(Rotas.editar(p.id)) }) { Text("Revisar") }
                    }
                }
            }
            item {
                Secao("3 · Publicar") {
                    Text("Na Fila, toque em \"Publicar todos\": o app agenda um a cada ${app.prefs.intervaloMinutos} min no Telegram.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { nav.irPara("fila") }) { Text("Ir para a Fila") }
                }
            }
            item { Rodape() }
        }
    }
}
