package bot.afiliados.domain

enum class EstadoFila(val rotulo: String, val final: Boolean = false) {
    RASCUNHO("Rascunho"),
    AGUARDANDO_REVISAO("Aguardando revisão"),
    AGUARDANDO_VERIFICACAO_PRECO("Aguardando verificação de preço"),
    PRONTO("Pronto para publicar"),
    AGENDADO("Agendado"),
    EM_PROCESSAMENTO("Em processamento"),
    PUBLICADO_CONFIRMADO("Publicado com confirmação", final = true),
    PUBLICADO_INFORMADO("Publicado (informado por você)", final = true),
    ENVIO_NAO_CONFIRMADO("Envio não confirmado"),
    FALHA("Falha"),
    CANCELADO("Cancelado", final = true),
}

enum class TipoCanal(val rotulo: String, val automatico: Boolean) {
    WHATSAPP_COMPARTILHAR("WhatsApp (compartilhar)", false),
    COMPARTILHAR("Outro app (compartilhar)", false),
    TELEGRAM("Telegram (automático)", true),
    WHATSAPP_AUTO("WhatsApp (automático, número próprio)", true),
}

/** Máquina de estados da fila. Toda mudança passa por aqui. */
object Fila {
    private val permitidas: Map<EstadoFila, Set<EstadoFila>> =
        mapOf(
            EstadoFila.RASCUNHO to setOf(EstadoFila.AGUARDANDO_REVISAO, EstadoFila.PRONTO, EstadoFila.CANCELADO),
            EstadoFila.AGUARDANDO_REVISAO to setOf(EstadoFila.RASCUNHO, EstadoFila.PRONTO, EstadoFila.AGUARDANDO_VERIFICACAO_PRECO, EstadoFila.CANCELADO),
            EstadoFila.AGUARDANDO_VERIFICACAO_PRECO to setOf(EstadoFila.PRONTO, EstadoFila.AGUARDANDO_REVISAO, EstadoFila.CANCELADO),
            EstadoFila.PRONTO to setOf(EstadoFila.AGENDADO, EstadoFila.EM_PROCESSAMENTO, EstadoFila.PUBLICADO_INFORMADO, EstadoFila.AGUARDANDO_VERIFICACAO_PRECO, EstadoFila.AGUARDANDO_REVISAO, EstadoFila.CANCELADO),
            EstadoFila.AGENDADO to setOf(EstadoFila.PRONTO, EstadoFila.EM_PROCESSAMENTO, EstadoFila.PUBLICADO_INFORMADO, EstadoFila.AGUARDANDO_VERIFICACAO_PRECO, EstadoFila.CANCELADO),
            EstadoFila.EM_PROCESSAMENTO to setOf(EstadoFila.PUBLICADO_CONFIRMADO, EstadoFila.ENVIO_NAO_CONFIRMADO, EstadoFila.FALHA),
            // Envio sem resposta: só pode tentar de novo (mesma chave, o backend não duplica) ou eu confirmo/cancelo.
            EstadoFila.ENVIO_NAO_CONFIRMADO to setOf(EstadoFila.EM_PROCESSAMENTO, EstadoFila.PUBLICADO_INFORMADO, EstadoFila.CANCELADO),
            EstadoFila.FALHA to setOf(EstadoFila.PRONTO, EstadoFila.AGUARDANDO_REVISAO, EstadoFila.CANCELADO),
            EstadoFila.PUBLICADO_CONFIRMADO to emptySet(),
            EstadoFila.PUBLICADO_INFORMADO to emptySet(),
            EstadoFila.CANCELADO to emptySet(),
        )

    fun pode(de: EstadoFila, para: EstadoFila) = para in (permitidas[de] ?: emptySet())

    fun transicao(de: EstadoFila, para: EstadoFila): EstadoFila {
        require(pode(de, para)) { "Mudança inválida: ${de.rotulo} → ${para.rotulo}" }
        return para
    }

    /** Antes de publicar: o que impede? Lista vazia = pode seguir. */
    fun bloqueios(texto: String, link: String?, situacaoPreco: SituacaoPreco, precoConfirmadoAgora: Boolean): List<String> = buildList {
        if (texto.isBlank()) add("Falta o texto da oferta")
        if (link.isNullOrBlank()) add("Falta o link")
        else if (Links.validar(link, exigirConhecido = false) !is Links.Resultado.Ok) add("O link não é HTTPS válido")
        if (!Precos.podePublicarSemAviso(situacaoPreco) && !precoConfirmadoAgora) add("Preço ${situacaoPreco.rotulo.lowercase()}: verifique ou confirme antes")
        if (situacaoPreco == SituacaoPreco.PRODUTO_INDISPONIVEL) add("Produto indisponível")
        if (situacaoPreco == SituacaoPreco.LINK_INVALIDO) add("Link inválido")
    }

    /** Estados em que um mesmo produto já ocupa o canal (evita oferta repetida). */
    val ativos = EstadoFila.entries.filter { !it.final && it != EstadoFila.FALHA }
}

enum class StatusComissao(val rotulo: String) {
    ESTIMADA("Estimada"),
    REGISTRADA("Registrada (não confirmada)"),
    CONFIRMADA("Confirmada pela loja"),
    PAGA("Paga"),
}
