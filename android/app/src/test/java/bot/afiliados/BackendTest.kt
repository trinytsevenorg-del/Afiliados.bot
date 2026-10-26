package bot.afiliados

import bot.afiliados.data.AfiliadosDao
import bot.afiliados.data.Backend
import bot.afiliados.data.Backup
import bot.afiliados.data.FalhaBackend
import bot.afiliados.data.PedidoTelegram
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit

class BackendTest {
    private lateinit var srv: MockWebServer

    @Before fun abrir() { srv = MockWebServer().apply { useHttps(TesteTls.servidor(), false); start() } }
    @After fun fechar() { srv.shutdown() }

    private fun cliente(token: String? = "tok") = Backend(
        { srv.url("/").toString().trimEnd('/') }, { token },
        OkHttpClient.Builder().sslSocketFactory(TesteTls.clienteFactory(), TesteTls.clienteTrust()).readTimeout(1, TimeUnit.SECONDS).build(),
    )

    @Test fun semTokenNemChamaARede() = runTest {
        try { cliente(null).saude(); fail() } catch (e: FalhaBackend.NaoConfigurado) { }
        assertEquals(0, srv.requestCount)
    }

    @Test fun enviaTokenEErroVemExplicado() = runTest {
        srv.enqueue(MockResponse().setResponseCode(403).setBody("""{"erro":"sem_acesso","mensagem":"Sua conta ainda não tem acesso"}"""))
        try { cliente().status(true); fail() } catch (e: FalhaBackend.Recusado) {
            assertEquals("sem_acesso", e.codigo)
            assertEquals("Sua conta ainda não tem acesso", e.message)
        }
        assertEquals("Bearer tok", srv.takeRequest().getHeader("Authorization"))
    }

    @Test fun semRespostaNaoViraSucessoNemFalhaDefinitiva() = runTest {
        srv.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        try { cliente().publicarTelegram(PedidoTelegram("fila-1-abc", "@canal", "x")); fail() } catch (e: FalhaBackend.SemResposta) { }
    }

    @Test fun leituraDePrintTrazSoOQueVeio() = runTest {
        srv.enqueue(MockResponse().setBody("""{"dados":{"marketplace":"mercadolivre","nome":"Fone","preco":59.9,"precoAnterior":null,"comissaoPercentual":17,"caracteristicas":["Bluetooth 5.3"],"descricao":null,"avaliacao":null,"urgencia":null,"avisos":["Preço riscado ilegível"]},"lidoEm":"x","restantesHoje":3}"""))
        val r = cliente().lerPrint(listOf(bot.afiliados.data.ImagemPrint("image/jpeg", "AAAA")))
        assertEquals(17.0, r.dados.comissaoPercentual!!, 0.0)
        assertEquals(null, r.dados.precoAnterior)
        assertTrue(srv.takeRequest().body.readUtf8().contains("\"tipo\":\"image/jpeg\""))
    }

    @Test fun respostaDoTelegramTrazConfirmacao() = runTest {
        srv.enqueue(MockResponse().setBody("""{"chatId":"-100","messageId":77,"publicadoEm":"2026-10-26T00:00:00Z","repetido":false}"""))
        val r = cliente().publicarTelegram(PedidoTelegram("fila-1-abc", "@canal", "x"))
        assertEquals(77L, r.messageId)
    }
}

class BackupTest {
    private val daoVazio = Proxy.newProxyInstance(AfiliadosDao::class.java.classLoader, arrayOf(AfiliadosDao::class.java)) { _, _, _ -> error("não usado") } as AfiliadosDao
    private val b = Backup(daoVazio)

    @Test fun recusaArquivoDeOutroApp() {
        try { b.ler("""{"formato":"outro","versao":1}"""); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("outro")) }
        try { b.ler("não é json"); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun recusaLinkSemHttps() {
        val json = """{"formato":"afiliados.bot/backup","versao":1,"produtos":[{"id":1,"nome":"X","linkOriginal":"http://a.com","chave":"a","marketplace":"OUTRO"}]}"""
        try { b.ler(json); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("HTTPS")) }
    }

    @Test fun leBackupValido() {
        val json = """{"formato":"afiliados.bot/backup","versao":1,"produtos":[{"id":1,"nome":"X","linkOriginal":"https://a.com","chave":"a","marketplace":"OUTRO"}]}"""
        assertEquals(1, b.ler(json).produtos.size)
    }
}
