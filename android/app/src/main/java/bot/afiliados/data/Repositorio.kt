package bot.afiliados.data

import android.database.sqlite.SQLiteConstraintException
import bot.afiliados.domain.EstadoFila
import bot.afiliados.domain.Fila
import bot.afiliados.domain.Links
import bot.afiliados.domain.Marketplace
import bot.afiliados.domain.Precos
import bot.afiliados.domain.SituacaoLink
import bot.afiliados.domain.SituacaoPreco
import bot.afiliados.domain.TipoCanal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Dados para preencher o formulário depois de analisar um link. Nada aqui é inventado. */
data class Rascunho(
    val linkOriginal: String,
    val marketplace: Marketplace,
    val chave: String,
    val idExterno: String? = null,
    val nome: String? = null,
    val preco: Double? = null,
    val precoAnterior: Double? = null,
    val precoAnteriorComprovado: Boolean = false,
    val comissaoPercentual: Double? = null,
    val imagemUrl: String? = null,
    val linkAfiliado: String? = null,
    val linkAfiliadoOrigem: String? = null,
    val urgenciaFato: String? = null,
    val fonte: String? = null,
    val avisos: List<String> = emptyList(),
    val jaExiste: Produto? = null,
)

class ProdutoRepetido(val existente: Produto) : Exception("Este produto já está no catálogo: ${existente.nome}")

class Repositorio(
    val dao: AfiliadosDao,
    val backend: Backend,
    val prefs: Preferencias,
    private val agora: () -> Long = System::currentTimeMillis,
) {
    suspend fun registrar(tipo: String, texto: String) {
        dao.inserir(Atividade(tipo = tipo, texto = texto, quando = agora()))
    }

    // ---------- Cadastro por link ----------

    /**
     * Valida o link, segue o encurtador pelo backend e busca dados nas fontes
     * oficiais que estiverem configuradas. Cada falha vira um aviso, nunca um dado.
     */
    suspend fun analisarLink(bruto: String): Rascunho {
        val v = Links.validar(Links.extrairDeTexto(bruto) ?: bruto, exigirConhecido = false)
        if (v is Links.Resultado.Erro) throw IllegalArgumentException(v.motivo)
        v as Links.Resultado.Ok
        val avisos = mutableListOf<String>()
        var url = v.url
        var pareceAfiliado = false
        var ids: Ids = Ids()
        if (v.marketplace != Marketplace.OUTRO) {
            try {
                val a = backend.analisar(v.url)
                url = a.urlFinal
                ids = a.ids
                pareceAfiliado = a.pareceAfiliado
                avisos += a.avisos
            } catch (e: FalhaBackend) {
                avisos += "Análise automática indisponível: ${e.message}"
            }
        } else {
            avisos += "Loja não integrada. Preencha os dados à mão."
        }
        val chave = Links.chave(url)
        var r = Rascunho(linkOriginal = url, marketplace = v.marketplace, chave = chave, avisos = avisos, jaExiste = dao.produtoPorChave(chave))
        if (pareceAfiliado) r = r.copy(linkAfiliado = v.url, linkAfiliadoOrigem = "manual", avisos = r.avisos + "O link colado parece ser de afiliado. Confira se é o seu.")

        val dados = try {
            when {
                ids.shopee != null -> backend.produtoShopee(ids.shopee!!.shopId, ids.shopee!!.itemId)
                ids.mercadolivre != null -> backend.produtoMl(ids.mercadolivre!!.itemId)
                else -> null
            }
        } catch (e: FalhaBackend) {
            r = r.copy(avisos = r.avisos + "Dados do produto indisponíveis: ${e.message}")
            null
        }
        if (dados != null) {
            r = r.copy(
                idExterno = dados.itemId,
                nome = dados.nome,
                preco = dados.preco,
                precoAnterior = dados.precoAnterior,
                precoAnteriorComprovado = dados.precoAnterior != null,
                comissaoPercentual = dados.comissaoPercentual,
                imagemUrl = dados.imagem?.takeIf { it.startsWith("https://") },
                linkAfiliado = dados.linkAfiliado ?: r.linkAfiliado,
                linkAfiliadoOrigem = if (dados.linkAfiliado != null) "shopee_api" else r.linkAfiliadoOrigem,
                urgenciaFato = dados.ofertaValidaAte?.let { "Oferta válida até " + dataHora(Instant.parse(it).toEpochMilli()) },
                fonte = dados.fonte,
            )
        }
        return r
    }

    /** Converte em link de afiliado SÓ pela API oficial da Shopee. */
    suspend fun gerarLinkShopee(linkOriginal: String): LinkAfiliado = backend.linkShopee(linkOriginal, prefs.subIdPadrao)

    suspend fun salvarNovo(p: Produto, fonteDados: String?, resultado: SituacaoPreco = SituacaoPreco.VERIFICADO): Long {
        dao.produtoPorChave(p.chave)?.let { throw ProdutoRepetido(it) }
        val id = try {
            dao.inserir(p.copy(criadoEm = agora(), atualizadoEm = agora()))
        } catch (e: SQLiteConstraintException) {
            throw ProdutoRepetido(dao.produtoPorChave(p.chave) ?: throw e)
        }
        if (fonteDados != null && p.preco != null) {
            dao.inserir(VerificacaoPreco(produtoId = id, preco = p.preco, precoAnterior = p.precoAnterior, fonte = fonteDados, resultado = resultado, consultadoEm = agora()))
        }
        registrar("produto", "Cadastrou ${p.nome}")
        return id
    }

    suspend fun salvarEdicao(p: Produto) {
        dao.atualizar(p.copy(atualizadoEm = agora()))
        registrar("produto", "Editou ${p.nome}")
    }

    /** Cópia proposital: recebe uma chave própria para não ser confundida com o original. */
    suspend fun duplicar(p: Produto): Long {
        val id = dao.inserir(p.copy(id = 0, nome = p.nome + " (cópia)", chave = p.chave + "#copia-" + agora(), criadoEm = agora(), atualizadoEm = agora()))
        registrar("produto", "Duplicou ${p.nome}")
        return id
    }

    suspend fun temHistorico(p: Produto) = dao.publicacoesDoProduto(p.id) > 0 || dao.historicoDoProduto(p.id) > 0

    suspend fun excluir(p: Produto) {
        dao.excluir(p)
        registrar("produto", "Excluiu ${p.nome}")
    }

    // ---------- Preços ----------

    suspend fun verificarPreco(p: Produto): VerificacaoPreco {
        val fonteDisponivel = p.marketplace != Marketplace.OUTRO && p.idExterno != null
        var resultado: SituacaoPreco
        var preco: Double? = null
        var anterior: Double? = null
        var fonte = "nenhuma"
        var detalhe = ""
        if (!fonteDisponivel) {
            resultado = SituacaoPreco.VERIFICACAO_INDISPONIVEL
            detalhe = "Sem fonte automática para este produto. Confirme à mão."
        } else {
            try {
                val d = when (p.marketplace) {
                    Marketplace.SHOPEE -> {
                        val (loja, item) = p.chave.removePrefix("shopee:").substringBefore('#').split(":").let { it.getOrNull(0) to it.getOrNull(1) }
                        if (loja == null || item == null) throw IllegalStateException("Código da Shopee ausente")
                        backend.produtoShopee(loja, item)
                    }
                    else -> backend.produtoMl(p.idExterno!!)
                }
                fonte = d.fonte
                preco = d.preco
                anterior = d.precoAnterior
                resultado = when {
                    d.situacao == "closed" -> SituacaoPreco.PRODUTO_INDISPONIVEL
                    d.preco == null -> SituacaoPreco.VERIFICACAO_INDISPONIVEL
                    else -> SituacaoPreco.VERIFICADO
                }
                if (d.situacao == "paused") detalhe = "Anúncio pausado pelo vendedor"
            } catch (e: FalhaBackend.Recusado) {
                resultado = if (e.status == 404 && p.marketplace == Marketplace.MERCADO_LIVRE) SituacaoPreco.LINK_INVALIDO else SituacaoPreco.VERIFICACAO_INDISPONIVEL
                detalhe = e.message ?: ""
            } catch (e: Exception) {
                resultado = SituacaoPreco.VERIFICACAO_INDISPONIVEL
                detalhe = e.message ?: "Falha"
            }
        }
        val dif = if (preco != null && p.preco != null) Math.round((preco - p.preco) * 100) / 100.0 else null
        val v = VerificacaoPreco(produtoId = p.id, preco = preco, precoAnterior = anterior, fonte = fonte, resultado = resultado, detalhe = detalhe, diferenca = dif, consultadoEm = agora())
        dao.inserir(v)
        // Só muda o preço salvo quando a consulta trouxe um valor de verdade.
        val atualizado = if (resultado == SituacaoPreco.VERIFICADO) {
            // Preço anterior só continua "comprovado" se a fonte voltou a informá-lo agora.
            p.copy(preco = preco, precoAnterior = anterior ?: p.precoAnterior, precoAnteriorComprovado = anterior != null,
                situacaoPreco = resultado, ultimaVerificacao = agora(), fontePreco = fonte, situacaoLink = SituacaoLink.OK)
        } else {
            // Falha de consulta não apaga a verificação anterior, mas também não a renova.
            p.copy(situacaoPreco = if (resultado == SituacaoPreco.VERIFICACAO_INDISPONIVEL && Precos.podePublicarSemAviso(p.situacaoPreco)) p.situacaoPreco else resultado,
                situacaoLink = if (resultado == SituacaoPreco.LINK_INVALIDO) SituacaoLink.INVALIDO else p.situacaoLink)
        }
        dao.atualizar(atualizado.copy(atualizadoEm = agora()))
        val txt = when (resultado) {
            SituacaoPreco.VERIFICADO -> "Preço de ${p.nome}: ${Precos.formatar(preco)}" + (dif?.takeIf { it != 0.0 }?.let { " (variação ${Precos.formatar(it)})" } ?: "")
            else -> "Verificação de ${p.nome}: ${resultado.rotulo}"
        }
        registrar("preco", txt)
        return v
    }

    suspend fun confirmarPrecoManual(p: Produto, preco: Double) {
        dao.inserir(VerificacaoPreco(produtoId = p.id, preco = preco, precoAnterior = null, fonte = "manual", resultado = SituacaoPreco.VERIFICADO_MANUAL,
            diferenca = p.preco?.let { Math.round((preco - it) * 100) / 100.0 }, consultadoEm = agora()))
        dao.atualizar(p.copy(preco = preco, situacaoPreco = SituacaoPreco.VERIFICADO_MANUAL, ultimaVerificacao = agora(), fontePreco = "manual", atualizadoEm = agora()))
        registrar("preco", "Você confirmou o preço de ${p.nome}: ${Precos.formatar(preco)}")
    }

    fun situacaoPreco(p: Produto) = Precos.situacaoAtual(p.situacaoPreco, p.ultimaVerificacao, prefs.validadePrecoHoras, agora())

    // ---------- Textos ----------

    suspend fun gerarTexto(p: Produto, canal: String, anterior: TextoPromocional?): Pair<Long, List<String>> {
        val nicho = p.nichoId?.let { dao.nicho(it) }
        val desconto = Precos.desconto(p.preco, p.precoAnterior, p.precoAnteriorComprovado)
        val r = backend.gerarTexto(
            PedidoTexto(
                nome = p.nome,
                marketplace = when (p.marketplace) { Marketplace.SHOPEE -> "shopee"; Marketplace.MERCADO_LIVRE -> "mercadolivre"; else -> "outro" },
                preco = p.preco,
                precoAnterior = p.precoAnterior.takeIf { p.precoAnteriorComprovado },
                descontoPercentual = desconto,
                caracteristicas = p.caracteristicas.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(15),
                descricaoOriginal = p.descricaoOriginal.take(2000).ifBlank { null },
                nicho = nicho?.nome,
                preferenciasTexto = nicho?.preferenciasTexto?.ifBlank { null },
                urgenciaFato = p.urgenciaFato,
                canal = canal,
                versaoAnterior = anterior?.whatsapp,
            ),
        )
        val t = r.textos
        val id = dao.inserir(
            TextoPromocional(produtoId = p.id, titulo = t.titulo, whatsapp = t.whatsapp, canal = t.canal, descricao = t.descricao, cta = t.cta,
                objetiva = t.objetiva, chamativa = t.chamativa, hashtags = t.hashtags.joinToString(" "), urgencia = t.urgencia,
                origem = "ia", modelo = r.modelo, avisos = r.avisos.joinToString("\n"), criadoEm = agora()),
        )
        registrar("texto", "IA gerou texto para ${p.nome}" + if (r.avisos.isNotEmpty()) " (com avisos)" else "")
        return id to r.avisos
    }

    suspend fun salvarTextoManual(produtoId: Long, texto: String) =
        dao.inserir(TextoPromocional(produtoId = produtoId, whatsapp = texto, canal = texto, objetiva = texto, origem = "manual", criadoEm = agora()))

    // ---------- Fila ----------

    sealed interface ResultadoFila {
        data class Criado(val id: Long) : ResultadoFila
        data class JaNaFila(val item: ItemFila) : ResultadoFila
        data class SemLink(val motivo: String) : ResultadoFila
    }

    suspend fun adicionarAFila(p: Produto, texto: String, hashtags: String, canalId: Long?): ResultadoFila {
        val link = p.linkAfiliado ?: return ResultadoFila.SemLink("Este produto ainda não tem link de afiliado")
        dao.itemAtivo(p.id, canalId, Fila.ativos)?.let { return ResultadoFila.JaNaFila(it) }
        val id = dao.inserir(ItemFila(produtoId = p.id, canalId = canalId, texto = texto, link = link, hashtags = hashtags,
            estado = EstadoFila.AGUARDANDO_REVISAO, chave = "fila-" + UUID.randomUUID().toString(), ordem = (dao.maiorOrdem() ?: 0) + 1,
            criadoEm = agora(), atualizadoEm = agora()))
        registrar("fila", "Adicionou ${p.nome} à fila")
        return ResultadoFila.Criado(id)
    }

    suspend fun mudar(item: ItemFila, novo: EstadoFila, resultado: String? = item.ultimoResultado) = dao.mudarEstado(item, novo, resultado)

    /** Executa a publicação automática (Telegram). Chamado pelo WorkManager. */
    suspend fun publicarAutomatico(itemId: Long): EstadoFila {
        val item = dao.itemAtivo(itemId)
        if (item.estado != EstadoFila.AGENDADO && item.estado != EstadoFila.PRONTO && item.estado != EstadoFila.ENVIO_NAO_CONFIRMADO) return item.estado
        val canal = item.canalId?.let { dao.canal(it) }
        if (canal == null || canal.tipo != TipoCanal.TELEGRAM || !canal.ativo) return item.estado
        val produto = dao.produtoAgora(item.produtoId) ?: return dao.mudarEstado(item, EstadoFila.CANCELADO, "Produto excluído").estado
        val sit = situacaoPreco(produto)
        val bloqueios = Fila.bloqueios(item.texto, item.link, sit, precoConfirmadoAgora = false)
        if (bloqueios.isNotEmpty()) {
            if (item.estado == EstadoFila.ENVIO_NAO_CONFIRMADO) return item.estado
            dao.mudarEstado(item, EstadoFila.AGUARDANDO_VERIFICACAO_PRECO, bloqueios.joinToString("; "))
            registrar("fila", "Publicação de ${produto.nome} parou: ${bloqueios.first()}")
            return EstadoFila.AGUARDANDO_VERIFICACAO_PRECO
        }
        val emProc = dao.mudarEstado(item, EstadoFila.EM_PROCESSAMENTO, "Enviando ao Telegram")
        val tentativa = dao.inserir(Tentativa(itemId = item.id, inicio = agora()))
        val (novo, detalhe, idExt) = try {
            val r = backend.publicarTelegram(PedidoTelegram(chave = item.chave, chatId = canal.destino, texto = bot.afiliados.domain.Mensagem.montar(item.texto, item.link, item.hashtags.split(' ').filter { it.isNotBlank() }), imagem = produto.imagemUrl,
                foto = produto.fotoLocal?.let { Imagens.paraEnvio(it) }))
            Triple(EstadoFila.PUBLICADO_CONFIRMADO, "Telegram confirmou a mensagem ${r.messageId}" + if (r.repetido) " (já tinha sido enviada antes)" else "", r.messageId.toString())
        } catch (e: FalhaBackend.SemResposta) {
            Triple(EstadoFila.ENVIO_NAO_CONFIRMADO, "Sem resposta. Pode ter sido enviada; tentar de novo não duplica.", null)
        } catch (e: FalhaBackend.SemConexao) {
            Triple(EstadoFila.FALHA, e.message ?: "Sem conexão", null)
        } catch (e: FalhaBackend) {
            Triple(EstadoFila.FALHA, e.message ?: "Falha", null)
        }
        dao.atualizar(Tentativa(id = tentativa, itemId = item.id, inicio = emProc.atualizadoEm, fim = agora(), resultado = novo, detalhe = detalhe))
        dao.mudarEstado(emProc, novo, detalhe, idExt)
        registrar("publicacao", "${produto.nome} → ${canal.nome}: ${novo.rotulo}")
        return novo
    }

    private suspend fun AfiliadosDao.itemAtivo(id: Long) = itemAgora(id) ?: throw IllegalStateException("Item $id não existe")

    /** Itens que ficaram "em processamento" (app fechado no meio) viram "envio não confirmado". */
    suspend fun recuperarInterrompidos(): Int {
        val presos = dao.itensNoEstado(EstadoFila.EM_PROCESSAMENTO).filter { agora() - it.atualizadoEm > 5 * 60_000 }
        presos.forEach { item ->
            dao.tentativaAberta(item.id)?.let { dao.atualizar(it.copy(fim = agora(), resultado = EstadoFila.ENVIO_NAO_CONFIRMADO, detalhe = "Interrompido")) }
            dao.mudarEstado(item, EstadoFila.ENVIO_NAO_CONFIRMADO, "Processo interrompido. Confira o canal antes de tentar de novo.")
        }
        return presos.size
    }

    companion object {
        private val fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())
        fun dataHora(ms: Long): String = fmt.format(Instant.ofEpochMilli(ms))
    }
}
