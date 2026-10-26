package bot.afiliados

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import bot.afiliados.data.BancoAfiliados
import bot.afiliados.data.Backend
import bot.afiliados.data.Canal
import bot.afiliados.data.canais
import bot.afiliados.data.comCanais
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
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
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

    @Test fun publicaTodasAsFotosDoProduto() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val arquivos = (1..3).map { i -> java.io.File(ctx.cacheDir, "f$i.jpg").apply { writeBytes(ByteArray(300) { i.toByte() }) } }
        val id = itemAgendadoNoTelegram()
        val item = banco.dao().itemAgora(id)!!
        val p = banco.dao().produtoAgora(item.produtoId)!!
        banco.dao().atualizar(p.copy(fotoLocal = arquivos.joinToString("\n") { it.path } + "\n/nao/existe.jpg"))
        srv.enqueue(MockResponse().setBody("""{"chatId":"-100","messageId":5,"publicadoEm":"x"}"""))
        assertEquals(EstadoFila.PUBLICADO_CONFIRMADO, repo.publicarAutomatico(id))
        val corpo = srv.takeRequest().body.readUtf8()
        assertEquals(3, Regex("\"tipo\":\"image/jpeg\"").findAll(corpo).count())
    }

    @Test fun loteDoPrintAteOAgendamentoEmSequencia() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val dao = banco.dao()
        val cozinha = dao.inserir(bot.afiliados.data.Nicho(nome = "Cozinha e utilidades"))
        val canal = dao.inserir(Canal(nome = "Tg", tipo = TipoCanal.TELEGRAM, destino = "@meucanal"))
        dao.atualizar(dao.nicho(cozinha)!!.copy(canalId = canal))
        val bmp = android.graphics.Bitmap.createBitmap(400, 800, android.graphics.Bitmap.Config.ARGB_8888)
        val arq = java.io.File(ctx.cacheDir, "lote.png").apply { outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        srv.enqueue(MockResponse().setBody("""{"dados":{"marketplace":"mercadolivre","nome":"Torneira Gourmet","preco":34.94,"precoAnterior":60.25,"comissaoPercentual":17,"caracteristicas":["Preto"],"nicho":"Cozinha e utilidades","cupom":null,"avisos":[]}}"""))
        srv.enqueue(MockResponse().setBody("""{"textos":{"titulo":"Torneira Gourmet: luxo na cozinha! ✨","whatsapp":"x","canal":"x","descricao":"x","cta":"x","objetiva":"x","chamativa":"x"},"modelo":"gemini"}"""))
        val id = repo.produtoDoPrint(ctx, android.net.Uri.fromFile(arq))
        val p = dao.produtoAgora(id)!!
        assertEquals(cozinha, p.nichoId)
        assertEquals(null, p.linkAfiliado)
        assertTrue(srv.takeRequest().body.readUtf8().contains("Cozinha e utilidades"))

        val r = repo.liberarComLink(p, "Olha: https://meli.la/abc")
        assertTrue(r is Repositorio.ResultadoFila.Criado)
        val item = dao.itemAgora((r as Repositorio.ResultadoFila.Criado).id)!!
        assertEquals(EstadoFila.PRONTO, item.estado)
        assertEquals(canal, item.canalId)
        assertTrue(item.texto.startsWith("💥 Torneira Gourmet: luxo na cozinha!"))
        assertTrue(item.texto.contains("~R$ 60,25~") && item.texto.contains("*R$ 34,94*"))

        val agendados = repo.agendarEmSequencia(repo.prontosAutomaticos(), 15)
        assertEquals(1, agendados.size)
        assertEquals(EstadoFila.AGENDADO, agendados[0].estado)
    }

    @Test fun agendamentoEmSequenciaEspacaPeloIntervalo() = runBlocking {
        val dao = banco.dao()
        val cid = dao.inserir(Canal(nome = "Tg", tipo = TipoCanal.TELEGRAM, destino = "@meucanal"))
        val itens = (1..3).map { i ->
            val pid = repo.salvarNovo(produto(chave = "k$i"), null)
            val r = repo.adicionarAFila(dao.produtoAgora(pid)!!, "t$i", "", cid) as Repositorio.ResultadoFila.Criado
            repo.mudar(dao.itemAgora(r.id)!!, EstadoFila.PRONTO)
        }
        val ag = repo.agendarEmSequencia(itens, 15)
        assertEquals(listOf(0L, 15L, 30L), ag.map { (it.planejadoPara!! - ag[0].planejadoPara!!) / 60_000 })
    }

    @Test fun nichoComVariosCanaisGeraUmItemPorCanalEAgendaPorCanal() = runBlocking {
        val dao = banco.dao()
        val tg = dao.inserir(Canal(nome = "Tg", tipo = TipoCanal.TELEGRAM, destino = "@meucanal"))
        val wa = dao.inserir(Canal(nome = "Zap", tipo = TipoCanal.WHATSAPP_AUTO, destino = "1@g.us"))
        val nicho = dao.inserir(bot.afiliados.data.Nicho(nome = "Casa"))
        dao.atualizar(dao.nicho(nicho)!!.comCanais(listOf(tg, wa)))
        assertEquals(listOf(tg, wa), dao.nicho(nicho)!!.canais)
        (1..2).forEach { i ->
            val pid = repo.salvarNovo(produto(chave = "c$i").copy(nichoId = nicho, linkAfiliado = null), null)
            assertTrue(repo.liberarComLink(dao.produtoAgora(pid)!!, "https://meli.la/x$i") is Repositorio.ResultadoFila.Criado)
        }
        val prontos = repo.prontosAutomaticos()
        assertEquals(listOf(tg, wa, tg, wa), prontos.map { it.canalId })
        // Em cada canal: 0 e 15 min. O segundo canal anda 1 min depois do primeiro.
        val ag = repo.agendarEmSequencia(prontos, 15).map { it.canalId to it.planejadoPara!! }
        val base = ag.minOf { it.second }
        assertEquals(listOf(tg to 0L, wa to 1L, tg to 15L, wa to 16L), ag.map { it.first to (it.second - base) / 60_000 })
    }

    @Test fun filaNoServidorAgendaSincronizaECancela() = runBlocking {
        val dao = banco.dao()
        val id = itemAgendadoNoTelegram()
        dao.atualizar(dao.itemAgora(id)!!.copy(planejadoPara = relogio + 15 * 60_000))
        srv.enqueue(MockResponse().setBody("""{"chave":"x","estado":"agendado"}"""))
        assertTrue(repo.agendarNoServidor(dao.itemAgora(id)!!))
        val pedido = srv.takeRequest()
        assertEquals("/api/fila", pedido.path)
        val corpo = pedido.body.readUtf8()
        assertTrue(corpo.contains("\"canal\":\"telegram\"") && corpo.contains("\"destino\":\"@meucanal\""))
        assertTrue(corpo.contains("\"validoAte\":${relogio + 6 * 3_600_000L}"))
        val item = dao.itemAgora(id)!!
        assertTrue(item.noServidor)
        assertEquals(EstadoFila.AGENDADO, item.estado)

        // Ainda não saiu: segue agendado com o recado do servidor.
        srv.enqueue(MockResponse().setBody("""{"itens":[{"chave":"${item.chave}","estado":"agendado","resultado":"Esperando: limite","quando":5}]}"""))
        assertEquals(0, repo.sincronizarServidor())
        assertEquals("Esperando: limite", dao.itemAgora(id)!!.ultimoResultado)
        // Saiu: vira publicado com o id da mensagem.
        srv.enqueue(MockResponse().setBody("""{"itens":[{"chave":"${item.chave}","estado":"publicado","resultado":"Telegram confirmou","mensagemId":"77","quando":5,"atualizadoEm":6}]}"""))
        assertEquals(1, repo.sincronizarServidor())
        assertEquals(EstadoFila.PUBLICADO_CONFIRMADO, dao.itemAgora(id)!!.estado)
        assertEquals("77", dao.itemAgora(id)!!.idMensagemExterna)
    }

    @Test fun cancelarDepoisQueOServidorPublicouNaoCancela() = runBlocking {
        val dao = banco.dao()
        val id = itemAgendadoNoTelegram()
        dao.atualizar(dao.itemAgora(id)!!.copy(noServidor = true))
        val chave = dao.itemAgora(id)!!.chave
        srv.enqueue(MockResponse().setBody("""{"chave":"$chave","estado":"publicado","mensagemId":"9"}"""))
        srv.enqueue(MockResponse().setBody("""{"itens":[{"chave":"$chave","estado":"publicado","mensagemId":"9"}]}"""))
        assertEquals(false, repo.tirarDoServidor(dao.itemAgora(id)!!))
        assertEquals(EstadoFila.PUBLICADO_CONFIRMADO, dao.itemAgora(id)!!.estado)
    }

    @Test fun semFilaNoServidorOCelularPublica() = runBlocking {
        val id = itemAgendadoNoTelegram()
        srv.enqueue(MockResponse().setResponseCode(503).setBody("""{"erro":"nao_configurado","mensagem":"Fila no servidor precisa do banco"}"""))
        assertEquals(false, repo.agendarNoServidor(banco.dao().itemAgora(id)!!))
        assertEquals(false, banco.dao().itemAgora(id)!!.noServidor)
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
