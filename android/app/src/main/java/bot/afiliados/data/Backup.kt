package bot.afiliados.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ArquivoBackup(
    val formato: String = FORMATO,
    val versao: Int = 1,
    val exportadoEm: Long = System.currentTimeMillis(),
    val nichos: List<Nicho> = emptyList(),
    val produtos: List<Produto> = emptyList(),
    val verificacoes: List<VerificacaoPreco> = emptyList(),
    val textos: List<TextoPromocional> = emptyList(),
    val canais: List<Canal> = emptyList(),
    val comissoes: List<Comissao> = emptyList(),
) {
    companion object { const val FORMATO = "afiliados.bot/backup" }
}

data class ResumoImportacao(val nichos: Int, val produtos: Int, val repetidos: Int, val textos: Int, val comissoes: Int, val canais: Int)

/**
 * Exporta e importa o catálogo em JSON. Tokens NÃO entram no backup.
 * Na importação, produtos que já existem (mesma chave) são pulados.
 * A fila não é importada: publicações antigas não devem ser reenviadas.
 */
class Backup(private val dao: AfiliadosDao) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    suspend fun exportar(): String = json.encodeToString(
        ArquivoBackup.serializer(),
        ArquivoBackup(
            nichos = dao.nichosLista(), produtos = dao.produtosLista(), verificacoes = dao.verificacoesLista(),
            textos = dao.textosLista(), canais = dao.canaisLista(), comissoes = dao.comissoesLista(),
        ),
    )

    fun ler(texto: String): ArquivoBackup {
        if (texto.length > 20_000_000) throw IllegalArgumentException("Arquivo grande demais")
        val b = try { json.decodeFromString(ArquivoBackup.serializer(), texto) } catch (e: Exception) {
            throw IllegalArgumentException("Arquivo inválido: não é um backup do Afiliados.bot")
        }
        if (b.formato != ArquivoBackup.FORMATO) throw IllegalArgumentException("Arquivo de outro aplicativo")
        if (b.versao > 1) throw IllegalArgumentException("Backup de uma versão mais nova do app")
        b.produtos.forEach {
            require(it.nome.isNotBlank() && it.chave.isNotBlank()) { "Produto sem nome ou chave no arquivo" }
            require(it.linkOriginal.startsWith("https://")) { "Link não HTTPS no arquivo: ${it.nome}" }
            require(it.linkAfiliado == null || it.linkAfiliado.startsWith("https://")) { "Link de afiliado não HTTPS: ${it.nome}" }
        }
        return b
    }

    suspend fun importar(texto: String): ResumoImportacao {
        val b = ler(texto)
        val mapaNicho = mutableMapOf<Long, Long>()
        var nichos = 0
        for (n in b.nichos) {
            val existente = dao.nichoPorNome(n.nome)
            mapaNicho[n.id] = existente?.id ?: dao.inserir(n.copy(id = 0, canalId = null)).also { nichos++ }
        }
        val mapaCanal = mutableMapOf<Long, Long>()
        val canaisAtuais = dao.canaisLista()
        var canais = 0
        for (c in b.canais) {
            val igual = canaisAtuais.firstOrNull { it.tipo == c.tipo && it.destino == c.destino && it.nome == c.nome }
            mapaCanal[c.id] = igual?.id ?: dao.inserir(c.copy(id = 0)).also { canais++ }
        }
        val mapaProduto = mutableMapOf<Long, Long>()
        var produtos = 0
        var repetidos = 0
        for (p in b.produtos) {
            val existente = dao.produtoPorChave(p.chave)
            if (existente != null) {
                repetidos++
                continue
            }
            mapaProduto[p.id] = dao.inserir(p.copy(id = 0, nichoId = p.nichoId?.let { mapaNicho[it] }, linkAfiliadoOrigem = p.linkAfiliadoOrigem ?: "importado"))
            produtos++
        }
        b.verificacoes.forEach { v -> mapaProduto[v.produtoId]?.let { dao.inserir(v.copy(id = 0, produtoId = it)) } }
        var textos = 0
        b.textos.forEach { t -> mapaProduto[t.produtoId]?.let { dao.inserir(t.copy(id = 0, produtoId = it)); textos++ } }
        var comissoes = 0
        val jaTem = dao.comissoesLista().map { Triple(it.criadoEm, it.valor, it.marketplace) }.toSet()
        b.comissoes.filter { Triple(it.criadoEm, it.valor, it.marketplace) !in jaTem }.forEach { c ->
            // Comissão de produto repetido entra solta para não perder o registro financeiro.
            dao.inserir(c.copy(id = 0, produtoId = c.produtoId?.let { mapaProduto[it] }))
            comissoes++
        }
        dao.inserir(Atividade(tipo = "backup", texto = "Importou $produtos produtos ($repetidos já existiam)"))
        return ResumoImportacao(nichos, produtos, repetidos, textos, comissoes, canais)
    }
}
