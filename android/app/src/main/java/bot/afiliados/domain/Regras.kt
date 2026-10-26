package bot.afiliados.domain

import java.net.URI
import java.util.Locale
import kotlin.math.roundToInt

enum class Marketplace(val rotulo: String) {
    SHOPEE("Shopee"), MERCADO_LIVRE("Mercado Livre"), OUTRO("Outro");
}

/** Situação do preço. Nunca diz "verificado" sem uma consulta de verdade. */
enum class SituacaoPreco(val rotulo: String) {
    NUNCA_VERIFICADO("Nunca verificado"),
    VERIFICADO("Preço verificado"),
    VERIFICADO_MANUAL("Confirmado por você"),
    POSSIVELMENTE_DESATUALIZADO("Possivelmente desatualizado"),
    VERIFICACAO_INDISPONIVEL("Verificação indisponível"),
    PRODUTO_INDISPONIVEL("Produto indisponível"),
    LINK_INVALIDO("Link inválido ou inacessível"),
}

enum class SituacaoLink(val rotulo: String) {
    NAO_VERIFICADO("Não verificado"), OK("Link conferido"), INVALIDO("Link inválido");
}

object Links {
    private val dominios = mapOf(
        Marketplace.SHOPEE to listOf("shopee.com.br", "shope.ee"),
        Marketplace.MERCADO_LIVRE to listOf("mercadolivre.com.br", "mercadolivre.com", "mercadolibre.com", "meli.la"),
    )

    sealed interface Resultado {
        data class Ok(val url: String, val marketplace: Marketplace) : Resultado
        data class Erro(val motivo: String) : Resultado
    }

    fun marketplaceDoHost(host: String): Marketplace? {
        val h = host.lowercase(Locale.ROOT).trimEnd('.')
        return dominios.entries.firstOrNull { (_, ds) -> ds.any { h == it || h.endsWith(".$it") } }?.key
    }

    /** Pega o primeiro link de um texto compartilhado ("Olha isso: https://..."). */
    fun extrairDeTexto(texto: String): String? =
        Regex("""https?://[^\s<>"]+""").find(texto)?.value?.trimEnd('.', ',', ')', '!')

    fun validar(bruto: String, exigirConhecido: Boolean = true): Resultado {
        val t = bruto.trim()
        if (t.isEmpty()) return Resultado.Erro("Cole um link")
        if (t.length > 2048) return Resultado.Erro("Link longo demais")
        val u = try { URI(t) } catch (_: Exception) { return Resultado.Erro("Isso não parece um link") }
        if (u.scheme?.lowercase() != "https") return Resultado.Erro("Só links HTTPS são aceitos")
        val host = u.host ?: return Resultado.Erro("Link sem domínio")
        if (u.rawUserInfo != null) return Resultado.Erro("Link com usuário/senha embutidos foi recusado")
        if (u.port != -1 && u.port != 443) return Resultado.Erro("Porta incomum recusada")
        val mp = marketplaceDoHost(host)
        if (mp == null && exigirConhecido) return Resultado.Erro("Domínio não reconhecido. Use Shopee ou Mercado Livre, ou cadastre como Outro.")
        return Resultado.Ok(t, mp ?: Marketplace.OUTRO)
    }

    /**
     * Chave para detectar produto repetido. Usa o código do produto quando dá;
     * senão, domínio + caminho, sem parâmetros de rastreio.
     */
    fun chave(url: String): String {
        val u = try { URI(url.trim()) } catch (_: Exception) { return url.trim().lowercase(Locale.ROOT) }
        val caminho = java.net.URLDecoder.decode(u.rawPath ?: "", "UTF-8")
        Regex("""-i\.(\d+)\.(\d+)""").find(caminho)?.let { return "shopee:${it.groupValues[1]}:${it.groupValues[2]}" }
        Regex("""/product/(\d+)/(\d+)""").find(caminho)?.let { return "shopee:${it.groupValues[1]}:${it.groupValues[2]}" }
        Regex("""\bMLB-?(\d{6,})""", RegexOption.IGNORE_CASE).find(caminho)?.let { return "ml:MLB${it.groupValues[1]}" }
        return "${u.host?.lowercase(Locale.ROOT)}${caminho.trimEnd('/')}"
    }
}

object Precos {
    /** Desconto só existe com os dois preços e o anterior comprovado. */
    fun desconto(preco: Double?, anterior: Double?, anteriorComprovado: Boolean): Int? {
        if (preco == null || anterior == null || !anteriorComprovado) return null
        if (preco <= 0 || anterior <= preco) return null
        val pct = ((anterior - preco) / anterior * 100).roundToInt()
        return pct.takeIf { it in 1..95 }
    }

    fun comissaoEstimada(preco: Double?, percentual: Double?): Double? {
        if (preco == null || percentual == null || preco <= 0 || percentual <= 0) return null
        return Math.round(preco * percentual) / 100.0
    }

    /** Aplica a validade: um preço verificado vira "possivelmente desatualizado" depois do prazo. */
    fun situacaoAtual(salva: SituacaoPreco, ultimaVerificacao: Long?, validadeHoras: Int, agora: Long): SituacaoPreco {
        if (salva != SituacaoPreco.VERIFICADO && salva != SituacaoPreco.VERIFICADO_MANUAL) return salva
        if (ultimaVerificacao == null) return SituacaoPreco.NUNCA_VERIFICADO
        val vencido = agora - ultimaVerificacao > validadeHoras * 3_600_000L
        return if (vencido) SituacaoPreco.POSSIVELMENTE_DESATUALIZADO else salva
    }

    fun podePublicarSemAviso(s: SituacaoPreco) = s == SituacaoPreco.VERIFICADO || s == SituacaoPreco.VERIFICADO_MANUAL

    fun formatar(v: Double?): String =
        if (v == null) "—" else "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%,.2f", v)

    /** Aceita "59,90", "1.299,90", "59.9". */
    fun ler(texto: String): Double? {
        val t = texto.trim().removePrefix("R$").trim()
        if (t.isEmpty()) return null
        val n = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
        return n.toDoubleOrNull()?.takeIf { it >= 0 }
    }
}

object Mensagem {
    /** Marca onde o link entra no modelo. Fica no texto salvo; vira o link só na hora de enviar. */
    const val LINK = "{link}"

    val MODELO_PADRAO = """
        💥 {titulo}

        De: ~{de}~ ❌
        💸 Por: *{por}* 🔥

        🔗 Compre aqui: {link}

        🎟️ Use o cupom: *{cupom}*

        ⚠️ Promoção sujeita a alteração a qualquer momento.
    """.trimIndent()

    /**
     * Preenche o modelo. Linha com campo vazio some (sem preço anterior
     * comprovado não tem "De"; sem cupom não tem cupom). Nada é inventado.
     */
    fun aplicarModelo(modelo: String, titulo: String, de: Double?, por: Double?, cupom: String?): String {
        val valores = mapOf(
            "titulo" to titulo.trim(),
            "de" to (de?.let { Precos.formatar(it) } ?: ""),
            "por" to (por?.let { Precos.formatar(it) } ?: ""),
            "cupom" to cupom?.trim().orEmpty(),
        )
        val linhas = modelo.lines().mapNotNull { linha ->
            var l = linha
            for ((k, v) in valores) {
                if (l.contains("{$k}")) {
                    if (v.isEmpty()) return@mapNotNull null
                    l = l.replace("{$k}", v)
                }
            }
            l
        }
        // Sem linhas em branco repetidas, sobra de campos removidos.
        return linhas.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** O link entra no lugar de {link}, exatamente como foi salvo; sem a marca, vai no final. */
    fun montar(texto: String, link: String, hashtags: List<String> = emptyList()): String {
        val corpo = texto.trim()
        if (corpo.contains(LINK)) return corpo.replace(LINK, link.trim())
        val tags = hashtags.filter { it.isNotBlank() }.joinToString(" ")
        return buildString {
            append(corpo)
            if (tags.isNotEmpty()) append("\n\n").append(tags)
            append("\n\n").append(link.trim())
        }
    }

    /**
     * Converte a formatação do WhatsApp (*negrito*, ~riscado~, _itálico_) para o HTML do Telegram.
     * Escapa o resto para nenhum caractere virar formatação por acidente.
     */
    fun paraTelegramHtml(texto: String): String {
        var t = texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        fun troca(marca: Char, tag: String) {
            val m = Regex.escape(marca.toString())
            t = t.replace(Regex("(?<![\\w$m])$m([^$m\\n]+?)$m(?![\\w$m])")) { "<$tag>${it.groupValues[1]}</$tag>" }
        }
        troca('*', "b")
        troca('~', "s")
        troca('_', "i")
        return t
    }
}
