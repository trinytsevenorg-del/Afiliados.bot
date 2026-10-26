package bot.afiliados.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteDatabase
import bot.afiliados.domain.EstadoFila
import kotlinx.coroutines.flow.Flow

data class ContagemNicho(val nichoId: Long?, val total: Int)
data class SomaComissao(val status: String, val total: Double, val quantidade: Int)
data class ContagemEstado(val estado: EstadoFila, val total: Int)

@Dao
interface AfiliadosDao {
    // Nichos
    @Query("SELECT * FROM nichos ORDER BY ordem, nome") fun nichos(): Flow<List<Nicho>>
    @Query("SELECT * FROM nichos ORDER BY ordem, nome") suspend fun nichosLista(): List<Nicho>
    @Query("SELECT * FROM nichos WHERE id = :id") suspend fun nicho(id: Long): Nicho?
    @Query("SELECT * FROM nichos WHERE nome = :nome COLLATE NOCASE LIMIT 1") suspend fun nichoPorNome(nome: String): Nicho?
    @Insert suspend fun inserir(n: Nicho): Long
    @Update suspend fun atualizar(n: Nicho)
    @Delete suspend fun excluir(n: Nicho)
    @Query("SELECT nichoId, COUNT(*) AS total FROM produtos WHERE arquivado = 0 GROUP BY nichoId") fun contagemPorNicho(): Flow<List<ContagemNicho>>

    // Produtos
    @Query("SELECT * FROM produtos WHERE arquivado = :arquivados ORDER BY atualizadoEm DESC") fun produtos(arquivados: Boolean = false): Flow<List<Produto>>
    @Query("SELECT * FROM produtos ORDER BY id") suspend fun produtosLista(): List<Produto>
    @Query("SELECT * FROM produtos WHERE id = :id") fun produto(id: Long): Flow<Produto?>
    @Query("SELECT * FROM produtos WHERE id = :id") suspend fun produtoAgora(id: Long): Produto?
    @Query("SELECT * FROM produtos WHERE chave = :chave LIMIT 1") suspend fun produtoPorChave(chave: String): Produto?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun inserir(p: Produto): Long
    @Update suspend fun atualizar(p: Produto)
    @Delete suspend fun excluir(p: Produto)
    @Query("SELECT COUNT(*) FROM fila WHERE produtoId = :id") suspend fun publicacoesDoProduto(id: Long): Int
    @Query("SELECT COUNT(*) FROM verificacoes_preco WHERE produtoId = :id") suspend fun historicoDoProduto(id: Long): Int

    // Preços
    @Insert suspend fun inserir(v: VerificacaoPreco): Long
    @Query("SELECT * FROM verificacoes_preco WHERE produtoId = :id ORDER BY consultadoEm DESC") fun historico(id: Long): Flow<List<VerificacaoPreco>>
    @Query("SELECT * FROM verificacoes_preco ORDER BY id") suspend fun verificacoesLista(): List<VerificacaoPreco>

    // Textos
    @Insert suspend fun inserir(t: TextoPromocional): Long
    @Update suspend fun atualizar(t: TextoPromocional)
    @Query("SELECT * FROM textos WHERE produtoId = :id ORDER BY criadoEm DESC") fun textos(id: Long): Flow<List<TextoPromocional>>
    @Query("SELECT * FROM textos ORDER BY id") suspend fun textosLista(): List<TextoPromocional>
    @Query("SELECT COUNT(DISTINCT produtoId) FROM textos") fun produtosComTexto(): Flow<Int>

    // Canais
    @Query("SELECT * FROM canais ORDER BY nome") fun canais(): Flow<List<Canal>>
    @Query("SELECT * FROM canais WHERE id = :id") suspend fun canal(id: Long): Canal?
    @Query("SELECT * FROM canais ORDER BY id") suspend fun canaisLista(): List<Canal>
    @Insert suspend fun inserir(c: Canal): Long
    @Update suspend fun atualizar(c: Canal)
    @Delete suspend fun excluir(c: Canal)

    // Fila
    @Query("SELECT * FROM fila ORDER BY CASE WHEN estado IN ('PUBLICADO_CONFIRMADO','PUBLICADO_INFORMADO','CANCELADO') THEN 1 ELSE 0 END, ordem, COALESCE(planejadoPara, criadoEm)")
    fun fila(): Flow<List<ItemFila>>
    @Query("SELECT * FROM fila WHERE id = :id") fun item(id: Long): Flow<ItemFila?>
    @Query("SELECT * FROM fila WHERE id = :id") suspend fun itemAgora(id: Long): ItemFila?
    @Query("SELECT * FROM fila WHERE estado = :estado") suspend fun itensNoEstado(estado: EstadoFila): List<ItemFila>
    @Query("SELECT * FROM fila WHERE produtoId = :produtoId AND canalId IS :canalId AND estado IN (:estados) LIMIT 1")
    suspend fun itemAtivo(produtoId: Long, canalId: Long?, estados: List<EstadoFila>): ItemFila?
    @Insert suspend fun inserir(i: ItemFila): Long
    @Update suspend fun atualizar(i: ItemFila)
    @Delete suspend fun excluir(i: ItemFila)
    @Query("SELECT estado, COUNT(*) AS total FROM fila GROUP BY estado") fun contagemFila(): Flow<List<ContagemEstado>>
    @Query("SELECT MAX(ordem) FROM fila") suspend fun maiorOrdem(): Int?

    // Tentativas
    @Insert suspend fun inserir(t: Tentativa): Long
    @Update suspend fun atualizar(t: Tentativa)
    @Query("SELECT * FROM tentativas WHERE itemId = :id ORDER BY inicio DESC") fun tentativas(id: Long): Flow<List<Tentativa>>
    @Query("SELECT * FROM tentativas WHERE itemId = :id AND fim IS NULL ORDER BY inicio DESC LIMIT 1") suspend fun tentativaAberta(id: Long): Tentativa?

    // Comissões
    @Query("SELECT * FROM comissoes ORDER BY COALESCE(dataVenda, criadoEm) DESC") fun comissoes(): Flow<List<Comissao>>
    @Query("SELECT * FROM comissoes ORDER BY id") suspend fun comissoesLista(): List<Comissao>
    @Query("SELECT status, SUM(valor) AS total, COUNT(*) AS quantidade FROM comissoes GROUP BY status") fun somaComissoes(): Flow<List<SomaComissao>>
    @Insert suspend fun inserir(c: Comissao): Long
    @Update suspend fun atualizar(c: Comissao)
    @Delete suspend fun excluir(c: Comissao)

    // Atividades
    @Insert suspend fun inserir(a: Atividade): Long
    @Query("SELECT * FROM atividades ORDER BY quando DESC LIMIT :n") fun atividades(n: Int = 15): Flow<List<Atividade>>
    @Query("DELETE FROM atividades WHERE quando < :antes") suspend fun limparAtividades(antes: Long)

    @Transaction
    suspend fun mudarEstado(item: ItemFila, novo: EstadoFila, resultado: String? = item.ultimoResultado, idExterno: String? = item.idMensagemExterna): ItemFila {
        val atualizado = item.copy(
            estado = bot.afiliados.domain.Fila.transicao(item.estado, novo),
            ultimoResultado = resultado,
            idMensagemExterna = idExterno,
            atualizadoEm = System.currentTimeMillis(),
        )
        atualizar(atualizado)
        return atualizado
    }
}

@Database(
    entities = [Nicho::class, Produto::class, VerificacaoPreco::class, TextoPromocional::class, Canal::class, ItemFila::class, Tentativa::class, Comissao::class, Atividade::class],
    version = 5,
    exportSchema = true,
)
abstract class BancoAfiliados : RoomDatabase() {
    abstract fun dao(): AfiliadosDao

    companion object {
        val NICHOS_INICIAIS = listOf(
            "📱" to "Tecnologia e eletrônicos", "🛋️" to "Casa e decoração", "👕" to "Moda e acessórios",
            "💄" to "Beleza e cuidados pessoais", "🍳" to "Cozinha e utilidades", "⚽" to "Esportes e lazer",
            "🔧" to "Ferramentas", "🎁" to "Ofertas variadas",
        )

        /** Versão 2: foto do produto guardada no aparelho. */
        val MIGRACAO_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE produtos ADD COLUMN fotoLocal TEXT")
            }
        }

        /** Versão 3: cupom do produto. */
        val MIGRACAO_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE produtos ADD COLUMN cupom TEXT")
            }
        }

        val MIGRACAO_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE nichos ADD COLUMN canaisExtras TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRACAO_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fila ADD COLUMN noServidor INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun criar(ctx: Context): BancoAfiliados =
            Room.databaseBuilder(ctx, BancoAfiliados::class.java, "afiliados.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        NICHOS_INICIAIS.forEachIndexed { i, (icone, nome) ->
                            db.execSQL(
                                "INSERT INTO nichos (nome, descricao, icone, preferenciasTexto, ordem, criadoEm) VALUES (?, '', ?, '', ?, ?)",
                                arrayOf(nome, icone, i, System.currentTimeMillis()),
                            )
                        }
                    }
                })
                .addMigrations(MIGRACAO_1_2, MIGRACAO_2_3, MIGRACAO_3_4, MIGRACAO_4_5)
                .build()
    }
}
