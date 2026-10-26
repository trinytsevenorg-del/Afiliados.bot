package bot.afiliados.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import bot.afiliados.domain.EstadoFila
import bot.afiliados.domain.Marketplace
import bot.afiliados.domain.SituacaoLink
import bot.afiliados.domain.SituacaoPreco
import bot.afiliados.domain.StatusComissao
import bot.afiliados.domain.TipoCanal
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "nichos", indices = [Index(value = ["nome"], unique = true)])
data class Nicho(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val descricao: String = "",
    val icone: String = "🏷️",
    val preferenciasTexto: String = "",
    val canalId: Long? = null,
    val ordem: Int = 0,
    val criadoEm: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "produtos",
    indices = [Index(value = ["chave"], unique = true), Index("nichoId"), Index("arquivado")],
    foreignKeys = [ForeignKey(entity = Nicho::class, parentColumns = ["id"], childColumns = ["nichoId"], onDelete = ForeignKey.SET_NULL)],
)
data class Produto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val linkOriginal: String,
    /** Código para evitar cadastro repetido (ex.: shopee:loja:item). */
    val chave: String,
    val linkAfiliado: String? = null,
    /** "manual", "shopee_api" ou "importado". */
    val linkAfiliadoOrigem: String? = null,
    val marketplace: Marketplace,
    val idExterno: String? = null,
    val nichoId: Long? = null,
    val preco: Double? = null,
    val precoAnterior: Double? = null,
    /** Só é true quando o preço anterior veio da loja/API ou eu conferi. */
    val precoAnteriorComprovado: Boolean = false,
    val comissaoPercentual: Double? = null,
    /** "manual" ou "shopee_api". */
    val comissaoOrigem: String? = null,
    val imagemUrl: String? = null,
    /** Até 10 fotos guardadas no aparelho, uma por linha. A primeira é a principal. */
    val fotoLocal: String? = null,
    val descricaoOriginal: String = "",
    /** Fatos do produto, um por linha. A IA só usa isso. */
    val caracteristicas: String = "",
    val observacoes: String = "",
    /** Código de cupom (opcional). Sem cupom, a linha some da mensagem. */
    val cupom: String? = null,
    val situacaoLink: SituacaoLink = SituacaoLink.NAO_VERIFICADO,
    val situacaoPreco: SituacaoPreco = SituacaoPreco.NUNCA_VERIFICADO,
    val ultimaVerificacao: Long? = null,
    val fontePreco: String? = null,
    /** Fato real de prazo (ex.: "Oferta válida até 26/10 23:59", vindo da API). */
    val urgenciaFato: String? = null,
    val arquivado: Boolean = false,
    val criadoEm: Long = System.currentTimeMillis(),
    val atualizadoEm: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "verificacoes_preco",
    indices = [Index("produtoId")],
    foreignKeys = [ForeignKey(entity = Produto::class, parentColumns = ["id"], childColumns = ["produtoId"], onDelete = ForeignKey.CASCADE)],
)
data class VerificacaoPreco(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val produtoId: Long,
    val preco: Double?,
    val precoAnterior: Double?,
    val fonte: String,
    val resultado: SituacaoPreco,
    val detalhe: String = "",
    val diferenca: Double? = null,
    val consultadoEm: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "textos",
    indices = [Index("produtoId")],
    foreignKeys = [ForeignKey(entity = Produto::class, parentColumns = ["id"], childColumns = ["produtoId"], onDelete = ForeignKey.CASCADE)],
)
data class TextoPromocional(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val produtoId: Long,
    val titulo: String = "",
    val whatsapp: String = "",
    val canal: String = "",
    val descricao: String = "",
    val cta: String = "",
    val objetiva: String = "",
    val chamativa: String = "",
    val hashtags: String = "",
    val urgencia: String? = null,
    /** "ia" ou "manual". */
    val origem: String,
    val modelo: String? = null,
    val avisos: String = "",
    val editado: Boolean = false,
    val criadoEm: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "canais")
data class Canal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val tipo: TipoCanal,
    /** Telegram: @canal ou ID numérico. Compartilhar: vazio. */
    val destino: String = "",
    val ativo: Boolean = true,
    val ultimoTeste: String? = null,
)

@Serializable
@Entity(
    tableName = "fila",
    indices = [Index("produtoId"), Index("canalId"), Index("estado"), Index(value = ["chave"], unique = true)],
    foreignKeys = [
        ForeignKey(entity = Produto::class, parentColumns = ["id"], childColumns = ["produtoId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Canal::class, parentColumns = ["id"], childColumns = ["canalId"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class ItemFila(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val produtoId: Long,
    val canalId: Long?,
    val texto: String,
    val link: String,
    val hashtags: String = "",
    val estado: EstadoFila = EstadoFila.RASCUNHO,
    val planejadoPara: Long? = null,
    /** Chave de idempotência: a mesma em todas as tentativas deste item. */
    val chave: String,
    val ordem: Int = 0,
    val ultimoResultado: String? = null,
    val idMensagemExterna: String? = null,
    val criadoEm: Long = System.currentTimeMillis(),
    val atualizadoEm: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "tentativas",
    indices = [Index("itemId")],
    foreignKeys = [ForeignKey(entity = ItemFila::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
)
data class Tentativa(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val inicio: Long = System.currentTimeMillis(),
    val fim: Long? = null,
    val resultado: EstadoFila? = null,
    val detalhe: String = "",
)

@Serializable
@Entity(
    tableName = "comissoes",
    indices = [Index("produtoId")],
    foreignKeys = [ForeignKey(entity = Produto::class, parentColumns = ["id"], childColumns = ["produtoId"], onDelete = ForeignKey.SET_NULL)],
)
data class Comissao(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val produtoId: Long? = null,
    val marketplace: Marketplace,
    val valor: Double,
    val percentual: Double? = null,
    val dataVenda: Long? = null,
    val status: StatusComissao,
    /** "manual" ou nome da fonte oficial. */
    val origem: String = "manual",
    val observacao: String = "",
    val criadoEm: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "atividades", indices = [Index("quando")])
data class Atividade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quando: Long = System.currentTimeMillis(),
    val tipo: String,
    val texto: String,
)

/** Fotos que ainda existem no aparelho, na ordem (a primeira é a principal). */
val Produto.fotos: List<java.io.File>
    get() = fotoLocal?.lines()?.filter { it.isNotBlank() }?.map { java.io.File(it) }?.filter { it.isFile }.orEmpty()
