package bot.afiliados

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import bot.afiliados.data.BancoAfiliados
import bot.afiliados.data.Backend
import bot.afiliados.data.Canal
import bot.afiliados.data.FalhaBackend
import bot.afiliados.data.Preferencias
import bot.afiliados.data.Produto
import bot.afiliados.data.ProdutoRepetido
import bot.afiliados.data.Repositorio
import bot.afiliados.domain.EstadoFila
import bot.afiliados.domain.Marketplace
import bot.afiliados.domain.SituacaoPreco
import bot.afiliados.domain.TipoCanal
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RepositorioTest {
    private lateinit var srv: MockWebServer
    private lateinit var banco: BancoAfiliados
    private lateinit var repo: Repositorio
    private var relogio = 1_800_000_000_000L
    private var configurado = true

    @Before fun abrir() {
        srv = MockWebServer().apply { useHttps(TesteTls.servidor(), false); start() }
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        banco = Room.inMemoryDatabaseBuilder(ctx, BancoAfiliados::class.java).allowMainThreadQueries().build()
        val http = OkHttpClient.Builder().sslSocketFactory(TesteTls.clienteFactory(), TesteTls.clienteTrust()).readTimeout(1, TimeUnit.SECONDS).build()
        val backend = Backend({ if (configurado) srv.url("/").toString().trimEnd('/') else "" }, { "tok" }, http)
        repo = Repositorio(banco.dao(), backend, Preferencias(ctx).apply { validadePrecoHoras = 6 }) { relogio }
    }

    @After fun fechar() { banco.close(); srv.shutdown() }

    private fun produto(chave: String = "shopee:1:2", verificado: Boolean = true) = Produto(
        nome = "Fone Bluetooth", linkOriginal = "https://shopee.com.br/Fone-i.1.2", chave = chave, marketplace = Marketplace.SHOPEE, idExterno = "2",
        linkAfiliado = "https://s.shopee.com.br/abc", preco = 59.9,
        situacaoPreco = if (verificado) SituacaoPreco.VERIFICADO else SituacaoPreco.NUNCA_VERIFICADO, ultimaVerificacao = if (verificado) relogio else null,
    )

    private suspend fun itemAgendadoNoTelegram(verificado: Boolean = true): Long {
        val dao = banco.dao()
        val pid = repo.salvarNovo(produto(verificado = verificado), null)
        val cid = dao.inserir(Canal(nome = "Canal", tipo = TipoCanal.TELEGRAM, destino = "@meucanal"))
        val r = repo.adicionarAFila(dao.produtoAgora(pid)!!, "Fone por R$ 59,90", "#fone", cid) as Repositorio.ResultadoFila.Criado
        val item = dao.itemAgora(r.id)!!
        val pronto = repo.mudar(item, EstadoFila.PRONTO)
        repo.mudar(pronto, EstadoFila.AGENDADO)
        return r.id
    }

    @Test fun produtoRepetidoNaoEntraDuasVezes() = runBlocking {
        repo.salvarNovo(produto(), null)
        try { repo.salvarNovo(produto().copy(nome = "Outro nome"), null); fail() } catch (e: ProdutoRepetido) { assertEquals("Fone Bluetooth", e.existente.nome) }
        // Duplicar de propósito funciona e gera chave própria.
        val copia = repo.duplicar(banco.dao().produtoPorChave("shopee:1:2")!!)
        assertTrue(banco.dao().produtoAgora(copia)!!.chave.startsWith("shopee:1:2#copia-"))
    }

    @Test fun mesmaOfertaNaoEntraDuasVezesNaFilaDoMesmoCanal() = runBlocking {
        val pid = repo.salvarNovo(produto(), null)
        val p = banco.dao().produtoAgora(pid)!!
        val a = repo.adicionarAFila(p, "texto", "", null)
        val b = repo.adicionarAFila(p, "outro texto", "", null)
        assertTrue(a is Repositorio.ResultadoFila.Criado)
        assertTrue(b is Repositorio.ResultadoFila.JaNaFila)
        val semLink = repo.adicionarAFila(p.copy(linkAfiliado = null), "x", "", 99)
        assertTrue(semLink is Repositorio.ResultadoFila.SemLink)
    }

    @Test fun publicaComConfirmacaoEUsaAChaveDoItem() = runBlocking {
        val id = itemAgendadoNoTelegram()
        srv.enqueue(MockResponse().setBody("""{"chatId":"-100","messageId":77,"publicadoEm":"x"}"""))
        assertEquals(EstadoFila.PUBLICADO_CONFIRMADO, repo.publicarAutomatico(id))
        val corpo = srv.takeRequest().body.readUtf8()
        val item = banco.dao().itemAgora(id)!!
        assertTrue(corpo.contains(item.chave))
        assertTrue("link no fim, sem alteração", corpo.contains("https://s.shopee.com.br/abc\""))
        assertEquals("77", item.idMensagemExterna)
    }

    @Test fun precoVencidoParaAPublicacaoSemChamarARede() = runBlocking {
        val id = itemAgendadoNoTelegram()
        relogio += 7 * 3_600_000L
        assertEquals(EstadoFila.AGUARDANDO_VERIFICACAO_PRECO, repo.publicarAutomatico(id))
        assertEquals(0, srv.requestCount)
    }

    @Test fun semRespostaViraNaoConfirmadoERetentativaUsaMesmaChave() = runBlocking {
        val id = itemAgendadoNoTelegram()
        srv.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(EstadoFila.ENVIO_NAO_CONFIRMADO, repo.publicarAutomatico(id))
        val primeira = srv.takeRequest().body.readUtf8()
        srv.enqueue(MockResponse().setBody("""{"chatId":"-100","messageId":77,"publicadoEm":"x","repetido":true}"""))
        assertEquals(EstadoFila.PUBLICADO_CONFIRMADO, repo.publicarAutomatico(id))
        val segunda = srv.takeRequest().body.readUtf8()
        val chave = banco.dao().itemAgora(id)!!.chave
        assertTrue(primeira.contains(chave) && segunda.contains(chave))
    }

    @Test fun recusaDoTelegramViraFalha() = runBlocking {
        val id = itemAgendadoNoTelegram()
        srv.enqueue(MockResponse().setResponseCode(400).setBody("""{"erro":"sem_acesso","mensagem":"bot não é admin"}"""))
        assertEquals(EstadoFila.FALHA, repo.publicarAutomatico(id))
        assertEquals("bot não é admin", banco.dao().itemAgora(id)!!.ultimoResultado)
    }

    @Test fun itemInterrompidoNaoFicaComoProcessando() = runBlocking {
        val id = itemAgendadoNoTelegram()
        val item = banco.dao().itemAgora(id)!!
        banco.dao().mudarEstado(item, EstadoFila.EM_PROCESSAMENTO)
        banco.dao().atualizar(banco.dao().itemAgora(id)!!.copy(atualizadoEm = relogio - 10 * 60_000))
        assertEquals(1, repo.recuperarInterrompidos())
        assertEquals(EstadoFila.ENVIO_NAO_CONFIRMADO, banco.dao().itemAgora(id)!!.estado)
    }

    @Test fun falhaNaVerificacaoNaoMudaOPreco() = runBlocking {
        val pid = repo.salvarNovo(produto(verificado = false), null)
        srv.enqueue(MockResponse().setResponseCode(503).setBody("""{"erro":"nao_configurado","mensagem":"Shopee não configurada"}"""))
        val v = repo.verificarPreco(banco.dao().produtoAgora(pid)!!)
        assertEquals(SituacaoPreco.VERIFICACAO_INDISPONIVEL, v.resultado)
        val p = banco.dao().produtoAgora(pid)!!
        assertEquals(59.9, p.preco!!, 0.0)
        assertNull(p.ultimaVerificacao)
    }

    @Test fun verificacaoComSucessoAtualizaPrecoEHistorico() = runBlocking {
        val pid = repo.salvarNovo(produto(verificado = false), null)
        srv.enqueue(MockResponse().setBody("""{"fonte":"shopee_affiliate_api","consultadoEm":"x","itemId":"2","preco":54.9}"""))
        val v = repo.verificarPreco(banco.dao().produtoAgora(pid)!!)
        assertEquals(SituacaoPreco.VERIFICADO, v.resultado)
        assertEquals(-5.0, v.diferenca!!, 0.001)
        assertEquals(54.9, banco.dao().produtoAgora(pid)!!.preco!!, 0.0)
    }

    @Test fun semBackendAnaliseSoAvisaENaoInventaDados() = runBlocking {
        configurado = false
        val r = repo.analisarLink("Olha: https://shopee.com.br/Fone-i.1.2?sp=1")
        assertNull(r.nome)
        assertNull(r.preco)
        assertNull(r.linkAfiliado)
        assertTrue(r.avisos.isNotEmpty())
        assertEquals("shopee:1:2", r.chave)
    }

    @Test fun iaIndisponivelDaErroClaro() = runBlocking {
        val pid = repo.salvarNovo(produto(), null)
        srv.enqueue(MockResponse().setResponseCode(503).setBody("""{"erro":"nao_configurado","mensagem":"IA não configurada"}"""))
        try { repo.gerarTexto(banco.dao().produtoAgora(pid)!!, "whatsapp", null); fail() } catch (e: FalhaBackend.Recusado) { assertEquals("IA não configurada", e.message) }
        val pedido = srv.takeRequest().body.readUtf8()
        // Sem preço anterior comprovado, não vai desconto para a IA.
        assertTrue(!pedido.contains("descontoPercentual\":4"))
    }
}
