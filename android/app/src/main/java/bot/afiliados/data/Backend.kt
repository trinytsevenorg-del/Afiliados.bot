package bot.afiliados.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/** Erros do backend em categorias que a interface sabe explicar. */
sealed class FalhaBackend(msg: String) : Exception(msg) {
    class NaoConfigurado : FalhaBackend("Backend não configurado. Abra Configurações e informe o endereço e o token.")
    class SemConexao(msg: String) : FalhaBackend(msg)
    /** O pedido pode ter chegado ao servidor; não sabemos o resultado. */
    class SemResposta : FalhaBackend("Sem resposta do backend (tempo esgotado)")
    class Recusado(val status: Int, val codigo: String, msg: String) : FalhaBackend(msg)
}

@Serializable data class ErroApi(val erro: String = "", val mensagem: String = "")
@Serializable data class Saude(val ok: Boolean, val versao: String = "", val banco: String = "")
@Serializable data class Operacao(val id: String, val descricao: String, val implementada: Boolean, val disponivel: Boolean, val motivo: String? = null)
@Serializable data class StatusIntegracao(val id: String, val nome: String, val estado: String, val detalhe: String, val operacoes: List<Operacao> = emptyList(), val comoConfigurar: String? = null, val verificadoEm: String = "")
@Serializable data class ListaStatus(val integracoes: List<StatusIntegracao>)

@Serializable data class IdsShopee(val shopId: String, val itemId: String)
@Serializable data class IdsMl(val itemId: String)
@Serializable data class Ids(val shopee: IdsShopee? = null, val mercadolivre: IdsMl? = null)
@Serializable data class AnaliseLink(val marketplace: String, val urlInformada: String, val urlFinal: String, val ids: Ids = Ids(), val pareceAfiliado: Boolean = false, val avisos: List<String> = emptyList())

@Serializable
data class DadosProduto(
    val fonte: String,
    val consultadoEm: String,
    val itemId: String,
    val shopId: String? = null,
    val nome: String? = null,
    val preco: Double? = null,
    val precoAnterior: Double? = null,
    val comissaoPercentual: Double? = null,
    val imagem: String? = null,
    val linkProduto: String? = null,
    val linkAfiliado: String? = null,
    val situacao: String? = null,
    val ofertaValidaAte: String? = null,
)

@Serializable data class LinkAfiliado(val linkAfiliado: String, val fonte: String, val confirmado: Boolean)

@Serializable
data class PedidoTexto(
    val nome: String,
    val marketplace: String,
    val preco: Double? = null,
    val precoAnterior: Double? = null,
    val descontoPercentual: Int? = null,
    val caracteristicas: List<String> = emptyList(),
    val descricaoOriginal: String? = null,
    val nicho: String? = null,
    val preferenciasTexto: String? = null,
    val urgenciaFato: String? = null,
    val canal: String = "geral",
    val versaoAnterior: String? = null,
)

@Serializable
data class Textos(
    val titulo: String, val whatsapp: String, val canal: String, val descricao: String, val cta: String,
    val objetiva: String, val chamativa: String, val hashtags: List<String> = emptyList(), val urgencia: String? = null,
)
@Serializable data class RespostaTexto(val textos: Textos, val avisos: List<String> = emptyList(), val modelo: String = "", val restantesHoje: Int? = null)

@Serializable data class PedidoTelegram(val chave: String, val chatId: String, val texto: String, val imagem: String? = null, val fotos: List<ImagemPrint>? = null, val formato: String? = null)
@Serializable data class RespostaTelegram(val chatId: String, val messageId: Long, val publicadoEm: String, val repetido: Boolean = false)
@Serializable data class DestinoTelegram(val id: String, val titulo: String? = null, val tipo: String)

@Serializable data class ImagemPrint(val tipo: String, val base64: String)
@Serializable data class PedidoPrint(val imagens: List<ImagemPrint>, val nichos: List<String> = emptyList())
@Serializable
data class DadosPrint(
    val marketplace: String = "desconhecido",
    val nome: String? = null,
    val preco: Double? = null,
    val precoAnterior: Double? = null,
    val comissaoPercentual: Double? = null,
    val caracteristicas: List<String> = emptyList(),
    val descricao: String? = null,
    val avaliacao: Double? = null,
    val foto: FotoPrint? = null,
    val cupom: String? = null,
    val nicho: String? = null,
    val avisos: List<String> = emptyList(),
)
@Serializable data class FotoPrint(val imagem: Int, val caixa: List<Double>)
@Serializable data class RespostaPrint(val dados: DadosPrint, val restantesHoje: Int? = null)

@Serializable data class EstadoWhatsapp(val estado: String, val numero: String? = null, val erro: String? = null)
@Serializable data class CodigoWhatsapp(val codigo: String)
@Serializable data class GrupoWhatsapp(val id: String, val nome: String, val participantes: Int = 0, val souAdmin: Boolean = false, val soAdminsEnviam: Boolean = false)
@Serializable data class ListaGrupos(val grupos: List<GrupoWhatsapp>)
@Serializable data class PedidoWhatsapp(val chave: String, val grupo: String, val texto: String, val foto: ImagemPrint? = null)
@Serializable data class RespostaWhatsapp(val grupo: String, val mensagemId: String, val repetido: Boolean = false)
@Serializable data class Ok(val ok: Boolean = true)
@Serializable data class FilaAtiva(val ativa: Boolean = false)
@Serializable data class PedidoFila(
    val chave: String, val canal: String, val destino: String, val texto: String,
    val fotos: List<ImagemPrint>? = null, val imagem: String? = null, val quando: Long, val validoAte: Long? = null,
)
@Serializable data class SituacaoFila(val chave: String, val estado: String, val resultado: String? = null, val mensagemId: String? = null, val quando: Long = 0, val atualizadoEm: Long = 0)
@Serializable data class ListaSituacoes(val itens: List<SituacaoFila>)

class Backend(
    private val urlBase: () -> String,
    private val token: () -> String?,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(70, TimeUnit.SECONDS)
        .callTimeout(80, TimeUnit.SECONDS)
        .build(),
    /** Token da licença: o servidor do cliente só trabalha para app licenciado. */
    private val licenca: () -> String? = { null },
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val tipoJson = "application/json".toMediaType()

    val configurado get() = urlBase().startsWith("https://") && !token().isNullOrBlank()

    private suspend fun <T> chamar(metodo: String, caminho: String, corpo: String?, leitor: KSerializer<T>): T = withContext(Dispatchers.IO) {
        val base = urlBase()
        val tok = token()
        if (!base.startsWith("https://") || tok.isNullOrBlank()) throw FalhaBackend.NaoConfigurado()
        val req = Request.Builder()
            .url(base + caminho)
            .header("Authorization", "Bearer $tok")
            .apply { licenca()?.let { header("X-Licenca", it) } }
            .method(metodo, corpo?.toRequestBody(tipoJson))
            .build()
        val resp = try {
            http.newCall(req).execute()
        } catch (e: InterruptedIOException) {
            throw FalhaBackend.SemResposta()
        } catch (e: IOException) {
            throw FalhaBackend.SemConexao("Sem conexão com o backend. Confira a internet e o endereço.")
        }
        resp.use {
            val texto = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val e = runCatching { json.decodeFromString(ErroApi.serializer(), texto) }.getOrNull()
                throw FalhaBackend.Recusado(it.code, e?.erro ?: "http", e?.mensagem?.ifBlank { null } ?: "O backend respondeu ${it.code}")
            }
            json.decodeFromString(leitor, texto)
        }
    }

    private inline fun <reified B> corpo(b: B, s: KSerializer<B>) = json.encodeToString(s, b)

    suspend fun saude() = chamar("GET", "/api/health", null, Saude.serializer())
    suspend fun status(testar: Boolean) = chamar("GET", "/api/integrations/status" + if (testar) "?testar=1" else "", null, ListaStatus.serializer())

    @Serializable private data class ComUrl(val url: String)
    @Serializable private data class ComItem(val itemId: String)
    @Serializable private data class ComLoja(val shopId: String, val itemId: String)
    @Serializable private data class ComSubIds(val url: String, val subIds: List<String>)
    @Serializable private data class ComChat(val chatId: String)

    suspend fun analisar(url: String) = chamar("POST", "/api/links/analyze", corpo(ComUrl(url), ComUrl.serializer()), AnaliseLink.serializer())
    suspend fun produtoShopee(shopId: String, itemId: String) = chamar("POST", "/api/shopee/product", corpo(ComLoja(shopId, itemId), ComLoja.serializer()), DadosProduto.serializer())
    suspend fun linkShopee(url: String, subId: String) = chamar("POST", "/api/shopee/affiliate-link", corpo(ComSubIds(url, listOf(subId).filter { it.isNotBlank() }), ComSubIds.serializer()), LinkAfiliado.serializer())
    suspend fun produtoMl(itemId: String) = chamar("POST", "/api/ml/product", corpo(ComItem(itemId), ComItem.serializer()), DadosProduto.serializer())
    suspend fun gerarTexto(p: PedidoTexto) = chamar("POST", "/api/copy/generate", corpo(p, PedidoTexto.serializer()), RespostaTexto.serializer())
    suspend fun lerPrint(imagens: List<ImagemPrint>, nichos: List<String> = emptyList()) = chamar("POST", "/api/product/from-images", corpo(PedidoPrint(imagens, nichos), PedidoPrint.serializer()), RespostaPrint.serializer())
    suspend fun publicarTelegram(p: PedidoTelegram) = chamar("POST", "/api/publish/telegram", corpo(p, PedidoTelegram.serializer()), RespostaTelegram.serializer())
    @Serializable private data class ComTelefone(val telefone: String)
    suspend fun whatsappStatus() = chamar("GET", "/api/whatsapp/status", null, EstadoWhatsapp.serializer())
    suspend fun whatsappConectar(telefone: String) = chamar("POST", "/api/whatsapp/connect", corpo(ComTelefone(telefone), ComTelefone.serializer()), CodigoWhatsapp.serializer())
    suspend fun whatsappDesconectar() = chamar("POST", "/api/whatsapp/disconnect", "{}", Ok.serializer())
    suspend fun whatsappGrupos() = chamar("GET", "/api/whatsapp/groups", null, ListaGrupos.serializer())
    suspend fun publicarWhatsapp(p: PedidoWhatsapp) = chamar("POST", "/api/publish/whatsapp", corpo(p, PedidoWhatsapp.serializer()), RespostaWhatsapp.serializer())
    @Serializable private data class ComChaves(val chaves: List<String>)
    @Serializable private data class ComChave(val chave: String)
    suspend fun filaAtiva() = chamar("GET", "/api/fila/status", null, FilaAtiva.serializer()).ativa
    suspend fun filaAgendar(p: PedidoFila) = chamar("POST", "/api/fila", corpo(p, PedidoFila.serializer()), SituacaoFila.serializer())
    suspend fun filaConsultar(chaves: List<String>) = chamar("POST", "/api/fila/consultar", corpo(ComChaves(chaves), ComChaves.serializer()), ListaSituacoes.serializer()).itens
    suspend fun filaCancelar(chave: String) = chamar("POST", "/api/fila/cancelar", corpo(ComChave(chave), ComChave.serializer()), SituacaoFila.serializer())
    suspend fun testarTelegram(chatId: String) = chamar("POST", "/api/telegram/test-destination", corpo(ComChat(chatId), ComChat.serializer()), DestinoTelegram.serializer())
}
