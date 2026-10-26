package bot.afiliados.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import bot.afiliados.AfiliadosApp
import bot.afiliados.data.FalhaBackend
import bot.afiliados.data.Produto
import bot.afiliados.data.ProdutoRepetido
import bot.afiliados.data.Rascunho
import bot.afiliados.data.Repositorio
import bot.afiliados.domain.Links
import bot.afiliados.domain.Marketplace
import bot.afiliados.domain.Precos
import bot.afiliados.domain.SituacaoPreco
import kotlinx.coroutines.launch

/** Estado do formulário. Campos vazios ficam vazios: nada é preenchido por suposição. */
class FormProdutoVM(private val repo: Repositorio) : ViewModel() {
    var link by mutableStateOf("")
    var analisando by mutableStateOf(false)
    var salvando by mutableStateOf(false)
    var avisos by mutableStateOf<List<String>>(emptyList())
    var erro by mutableStateOf<String?>(null)
    var repetido by mutableStateOf<Produto?>(null)
    var analisado by mutableStateOf(false)
    var original: Produto? = null
    private var rascunho: Rascunho? = null

    var nome by mutableStateOf("")
    var linkAfiliado by mutableStateOf("")
    var linkAfiliadoOrigem by mutableStateOf<String?>(null)
    var marketplace by mutableStateOf(Marketplace.OUTRO)
    var nichoId by mutableStateOf<Long?>(null)
    var preco by mutableStateOf("")
    var precoAnterior by mutableStateOf("")
    var anteriorComprovado by mutableStateOf(false)
    var comissao by mutableStateOf("")
    var comissaoOrigem by mutableStateOf<String?>(null)
    var imagem by mutableStateOf("")
    var fotoLocal by mutableStateOf<String?>(null)
    var descricao by mutableStateOf("")
    var caracteristicas by mutableStateOf("")
    var observacoes by mutableStateOf("")
    var gerandoLink by mutableStateOf(false)
    var lendoPrint by mutableStateOf(false)
    /** Preço lido do print: se continuar igual ao salvar, conta como "confirmado por você". */
    private var precoDoPrint: Double? = null

    fun carregar(id: Long) = viewModelScope.launch {
        val p = repo.dao.produtoAgora(id) ?: return@launch
        original = p
        analisado = true
        link = p.linkOriginal
        nome = p.nome; linkAfiliado = p.linkAfiliado.orEmpty(); linkAfiliadoOrigem = p.linkAfiliadoOrigem
        marketplace = p.marketplace; nichoId = p.nichoId
        preco = p.preco?.let { Precos.formatar(it).removePrefix("R$ ") }.orEmpty()
        precoAnterior = p.precoAnterior?.let { Precos.formatar(it).removePrefix("R$ ") }.orEmpty()
        anteriorComprovado = p.precoAnteriorComprovado
        comissao = p.comissaoPercentual?.toString().orEmpty(); comissaoOrigem = p.comissaoOrigem
        imagem = p.imagemUrl.orEmpty(); fotoLocal = p.fotoLocal; descricao = p.descricaoOriginal; caracteristicas = p.caracteristicas; observacoes = p.observacoes
    }

    fun analisar() = viewModelScope.launch {
        erro = null; avisos = emptyList(); repetido = null
        analisando = true
        try {
            val r = repo.analisarLink(link)
            rascunho = r
            link = r.linkOriginal
            marketplace = r.marketplace
            r.nome?.let { nome = it }
            r.preco?.let { preco = Precos.formatar(it).removePrefix("R$ ") }
            r.precoAnterior?.let { precoAnterior = Precos.formatar(it).removePrefix("R$ ") }
            anteriorComprovado = r.precoAnteriorComprovado
            r.comissaoPercentual?.let { comissao = it.toString(); comissaoOrigem = "shopee_api" }
            r.imagemUrl?.let { imagem = it }
            r.linkAfiliado?.let { linkAfiliado = it; linkAfiliadoOrigem = r.linkAfiliadoOrigem }
            avisos = r.avisos
            repetido = r.jaExiste
            analisado = true
        } catch (e: IllegalArgumentException) {
            erro = e.message
        } finally {
            analisando = false
        }
    }

    fun lerPrints(ctx: android.content.Context, uris: List<android.net.Uri>) = viewModelScope.launch {
        val cr = ctx.contentResolver
        if (uris.isEmpty()) return@launch
        erro = null
        lendoPrint = true
        try {
            val imagens = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { uris.take(10).map { bot.afiliados.data.Imagens.paraPrint(cr, it) } }
            val d = repo.backend.lerPrint(imagens).dados
            d.nome?.let { nome = it }
            // Recorta a foto do produto do próprio print (a IA só diz onde ela está).
            d.foto?.let { f ->
                uris.getOrNull(f.imagem)?.let { u ->
                    runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { bot.afiliados.data.Fotos.recortar(ctx, u, f.caixa) } }
                        .onSuccess { fotoLocal = it }
                }
            }
            d.preco?.let { preco = Precos.formatar(it).removePrefix("R$ "); precoDoPrint = it }
            d.precoAnterior?.let { precoAnterior = Precos.formatar(it).removePrefix("R$ "); anteriorComprovado = true }
            d.comissaoPercentual?.let { comissao = it.toString(); comissaoOrigem = "print" }
            if (d.caracteristicas.isNotEmpty()) {
                val atuais = caracteristicas.lines().map(String::trim).filter(String::isNotEmpty)
                caracteristicas = (atuais + d.caracteristicas.filter { it !in atuais }).joinToString("\n")
            }
            d.avaliacao?.let { n ->
                val nota = "Nota ${n.toString().replace('.', ',')} de 5"
                if (!caracteristicas.contains(nota)) caracteristicas = (caracteristicas.lines().filter(String::isNotBlank) + nota).joinToString("\n")
            }
            d.descricao?.takeIf { descricao.isBlank() }?.let { descricao = it }
            if (marketplace == Marketplace.OUTRO) marketplace = when (d.marketplace) {
                "shopee" -> Marketplace.SHOPEE
                "mercadolivre" -> Marketplace.MERCADO_LIVRE
                else -> marketplace
            }
            avisos = listOf("Lido do print pela IA. Confira cada campo antes de salvar.") + d.avisos
            analisado = true
        } catch (e: FalhaBackend) {
            erro = "Leitura do print indisponível: ${e.message}"
        } catch (e: Exception) {
            erro = e.message ?: "Não deu para ler a imagem"
        } finally {
            lendoPrint = false
        }
    }

    fun gerarLinkShopee(aoErro: (String) -> Unit) = viewModelScope.launch {
        gerandoLink = true
        try {
            val r = repo.gerarLinkShopee(link)
            linkAfiliado = r.linkAfiliado
            linkAfiliadoOrigem = "shopee_api"
        } catch (e: FalhaBackend) {
            aoErro("Conversão automática indisponível: ${e.message}")
        } finally {
            gerandoLink = false
        }
    }

    fun validar(): String? {
        if (link.isBlank() && linkAfiliado.isNotBlank()) link = linkAfiliado.trim()
        if (nome.isBlank()) return "Informe o nome"
        if (link.isBlank()) return "Cole o link de afiliado (toque em GANHOS no app da loja e copie)"
        if (Links.validar(link, exigirConhecido = false) !is Links.Resultado.Ok) return "Link inválido (precisa ser HTTPS)"
        if (linkAfiliado.isNotBlank() && Links.validar(linkAfiliado, exigirConhecido = false) !is Links.Resultado.Ok) return "Link de afiliado inválido (precisa ser HTTPS)"
        if (preco.isNotBlank() && Precos.ler(preco) == null) return "Preço inválido"
        if (precoAnterior.isNotBlank() && Precos.ler(precoAnterior) == null) return "Preço anterior inválido"
        if (comissao.isNotBlank() && (Precos.ler(comissao) ?: -1.0) !in 0.0..100.0) return "Comissão deve ser de 0 a 100%"
        if (imagem.isNotBlank() && !imagem.startsWith("https://")) return "Imagem precisa ser um link HTTPS"
        return null
    }

    fun salvar(aoSalvar: (Long) -> Unit) = viewModelScope.launch {
        validar()?.let { erro = it; return@launch }
        salvando = true
        val precoN = Precos.ler(preco)
        val anteriorN = Precos.ler(precoAnterior)
        val r = rascunho
        val o = original
        // O preço só conta como "verificado" se veio da fonte agora e não foi mudado à mão.
        val veioDaFonte = r?.fonte != null && r.preco != null && r.preco == precoN
        val doPrint = !veioDaFonte && precoN != null && precoN == precoDoPrint
        val base = Produto(
            id = o?.id ?: 0,
            nome = nome.trim(),
            linkOriginal = link.trim(),
            chave = o?.chave ?: r?.chave ?: Links.chave(link),
            linkAfiliado = linkAfiliado.trim().ifBlank { null },
            linkAfiliadoOrigem = if (linkAfiliado.isBlank()) null else linkAfiliadoOrigem ?: "manual",
            marketplace = marketplace,
            idExterno = r?.idExterno ?: o?.idExterno,
            nichoId = nichoId,
            preco = precoN,
            precoAnterior = anteriorN,
            precoAnteriorComprovado = anteriorN != null && anteriorComprovado,
            comissaoPercentual = Precos.ler(comissao),
            comissaoOrigem = if (comissao.isBlank()) null else comissaoOrigem ?: "manual",
            imagemUrl = imagem.trim().ifBlank { null },
            fotoLocal = fotoLocal,
            descricaoOriginal = descricao.trim(),
            caracteristicas = caracteristicas.trim(),
            observacoes = observacoes.trim(),
            situacaoPreco = when {
                veioDaFonte -> SituacaoPreco.VERIFICADO
                doPrint -> SituacaoPreco.VERIFICADO_MANUAL
                o != null && o.preco == precoN -> o.situacaoPreco
                else -> SituacaoPreco.NUNCA_VERIFICADO
            },
            ultimaVerificacao = if (veioDaFonte || doPrint) System.currentTimeMillis() else o?.ultimaVerificacao.takeIf { o?.preco == precoN },
            fontePreco = if (veioDaFonte) r?.fonte else if (doPrint) "print" else o?.fontePreco,
            urgenciaFato = r?.urgenciaFato ?: o?.urgenciaFato,
            arquivado = o?.arquivado ?: false,
            criadoEm = o?.criadoEm ?: System.currentTimeMillis(),
        )
        try {
            if (o == null) aoSalvar(
                when {
                    veioDaFonte -> repo.salvarNovo(base, r?.fonte)
                    doPrint -> repo.salvarNovo(base, "print", SituacaoPreco.VERIFICADO_MANUAL)
                    else -> repo.salvarNovo(base, null)
                },
            )
            else { repo.salvarEdicao(base); aoSalvar(o.id) }
        } catch (e: ProdutoRepetido) {
            repetido = e.existente
            erro = e.message
        } finally {
            salvando = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProdutoFormTela(app: AfiliadosApp, nav: NavHostController, produtoId: Long?, linkInicial: String?) {
    val vm: FormProdutoVM = viewModel { FormProdutoVM(app.repo) }
    val ctx = LocalContext.current
    val escopoTela = androidx.compose.runtime.rememberCoroutineScope()
    val nichos by app.repo.dao.nichos().collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(produtoId, linkInicial) {
        if (produtoId != null && vm.original == null) vm.carregar(produtoId)
        else if (linkInicial != null && vm.link.isBlank()) { vm.link = linkInicial; vm.analisar() }
    }
    val novo = produtoId == null
    val escolherPrints = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        vm.lerPrints(ctx, uris)
    }
    val escolherFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) escopoTela.launch {
            runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { bot.afiliados.data.Fotos.daGaleria(ctx, uri) } }
                .onSuccess { vm.fotoLocal = it }.onFailure { ctx.avisar(it.message ?: "Não deu para usar essa imagem") }
        }
    }
    // Print enviado pelo "Compartilhar" de outro app.
    val pendente = app.printPendente.value
    LaunchedEffect(pendente) {
        if (pendente.isNotEmpty()) { app.printPendente.value = emptyList(); vm.lerPrints(ctx, pendente) }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (novo) "Novo produto" else "Editar produto") }, navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
        })
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = espaco, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Campo(vm.link, { vm.link = it }, "Link do produto", ajuda = "Cole o link da Shopee ou do Mercado Livre (HTTPS)")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { vm.analisar() }, enabled = !vm.analisando && vm.link.isNotBlank()) { Text(if (vm.analisado) "Analisar de novo" else "Analisar") }
                    if (!vm.analisado) OutlinedButton(onClick = { vm.analisado = true; vm.marketplace = Links.marketplaceDoHost(runCatching { java.net.URI(vm.link.trim()).host }.getOrNull() ?: "") ?: Marketplace.OUTRO }) { Text("Preencher à mão") }
                    if (vm.analisando) CircularProgressIndicator(Modifier.padding(start = 8.dp))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { escolherPrints.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !vm.lendoPrint,
                    ) { Text(if (vm.lendoPrint) "Lendo o print…" else "📷 Preencher pelo print") }
                    if (vm.lendoPrint) CircularProgressIndicator()
                }
                Text("Mande até 10 prints do mesmo produto: topo do anúncio (com GANHOS %), preço, descrição e características. A IA junta tudo e preenche.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!vm.analisado) vm.erro?.let { Aviso(it, erro = true) }
            }
            vm.repetido?.let { r ->
                item {
                    Aviso("Este produto já está no catálogo: ${r.nome}", erro = true)
                    OutlinedButton(onClick = { nav.irPara(Rotas.produto(r.id)) }) { Text("Abrir o existente") }
                }
            }
            items(vm.avisos.size) { Aviso(vm.avisos[it]) }
            if (vm.analisado) {
                item { Campo(vm.nome, { vm.nome = it }, "Nome") }
                item { Escolha("Loja", Marketplace.entries, vm.marketplace, { it.rotulo }, { vm.marketplace = it }) }
                item { Escolha("Nicho", listOf(null) + nichos, nichos.firstOrNull { it.id == vm.nichoId }, { n -> n?.let { "${it.icone} ${it.nome}" } ?: "Sem nicho" }, { vm.nichoId = it?.id }) }
                item {
                    val origem = when (vm.linkAfiliadoOrigem) { "shopee_api" -> "Gerado pela API oficial da Shopee"; "manual" -> "Informado por você"; "importado" -> "Importado de backup"; else -> null }
                    Campo(vm.linkAfiliado, { vm.linkAfiliado = it; vm.linkAfiliadoOrigem = "manual" }, "Link de afiliado", ajuda = origem ?: "No app da loja, toque em GANHOS → copiar link. Depois toque em Colar.")
                    OutlinedButton(onClick = {
                        val texto = ctx.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                        val achado = Links.extrairDeTexto(texto)
                        if (achado == null) ctx.avisar("Não achei um link copiado. Copie o link de afiliado e tente de novo.")
                        else { vm.linkAfiliado = achado; vm.linkAfiliadoOrigem = "manual" }
                    }) { Text("📋 Colar link copiado") }
                }
                if (vm.marketplace == Marketplace.SHOPEE) item {
                    OutlinedButton(onClick = { vm.gerarLinkShopee { ctx.avisar(it) } }, enabled = !vm.gerandoLink) { Text(if (vm.gerandoLink) "Gerando…" else "Gerar link pela API da Shopee") }
                }
                if (vm.marketplace == Marketplace.MERCADO_LIVRE) item {
                    Text("Mercado Livre: não há API oficial para gerar link de afiliado. Gere no painel de Afiliados do ML e cole acima.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Campo(vm.preco, { vm.preco = it }, "Preço atual (R$)", Modifier.weight(1f), numero = true)
                        Campo(vm.precoAnterior, { vm.precoAnterior = it }, "Preço anterior (R$)", Modifier.weight(1f), numero = true)
                    }
                }
                if (vm.precoAnterior.isNotBlank()) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(vm.anteriorComprovado, { vm.anteriorComprovado = it })
                        Text("O preço anterior é comprovado (veio da loja ou eu conferi)", style = MaterialTheme.typography.bodyMedium)
                    }
                    val d = Precos.desconto(Precos.ler(vm.preco), Precos.ler(vm.precoAnterior), vm.anteriorComprovado)
                    Text(if (d != null) "Desconto calculado: $d%" else "Sem desconto calculado (precisa dos dois preços e do anterior comprovado)", style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Campo(vm.comissao, { vm.comissao = it; vm.comissaoOrigem = "manual" }, "Comissão (%)", numero = true,
                        ajuda = when (vm.comissaoOrigem) { "shopee_api" -> "Informada pela API da Shopee"; "print" -> "Lida do print (confira)"; "manual" -> "Informada por você"; else -> "Opcional" })
                    Precos.comissaoEstimada(Precos.ler(vm.preco), Precos.ler(vm.comissao))?.let {
                        Text("Comissão estimada por venda: ${Precos.formatar(it)} (estimativa)", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Secao("Foto do produto") {
                        val foto = vm.fotoLocal?.let { java.io.File(it) }?.takeIf { it.isFile }
                        if (foto != null) coil.compose.AsyncImage(foto, null, Modifier.fillMaxWidth().heightIn(max = 280.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                        else Text("Sem foto. Ela é recortada do print sozinha, ou escolha uma da galeria.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { escolherFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (foto != null) "Trocar foto" else "Escolher foto") }
                            if (foto != null) TextButton(onClick = { vm.fotoLocal = null }) { Text("Remover") }
                        }
                    }
                }
                item { Campo(vm.caracteristicas, { vm.caracteristicas = it }, "Características comprovadas (uma por linha)", linhas = 3, ajuda = "A IA só usa o que estiver aqui e na descrição") }
                item { Campo(vm.descricao, { vm.descricao = it }, "Descrição original", linhas = 3) }
                item { Campo(vm.observacoes, { vm.observacoes = it }, "Observações pessoais", linhas = 2) }
                vm.erro?.let { item { Aviso(it, erro = true) } }
                item {
                    Button(onClick = { vm.salvar { id -> nav.popBackStack(); if (novo) nav.irPara(Rotas.produto(id)) } }, enabled = !vm.salvando, modifier = Modifier.fillMaxWidth()) {
                        Text(if (novo) "Salvar no catálogo" else "Salvar alterações")
                    }
                }
            }
            item { Rodape() }
        }
    }
}
