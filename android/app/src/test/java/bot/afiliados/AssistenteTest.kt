package bot.afiliados

import bot.afiliados.data.Assistente
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AssistenteTest {
    @Test fun montaOEnderecoDoBanco() {
        val pooler = "postgresql://postgres.abc:[YOUR-PASSWORD]@aws-0-us-east-2.pooler.supabase.com:5432/postgres"
        assertEquals("postgresql://postgres.abc:Senha123@aws-0-us-east-2.pooler.supabase.com:5432/postgres", Assistente.montarBanco(" $pooler ", "Senha123").getOrThrow())
        assertTrue(Assistente.montarBanco("postgresql://postgres:x@db.abc.supabase.co:5432/postgres", "s").exceptionOrNull()!!.message!!.contains("Direct"))
        assertTrue(Assistente.montarBanco("https://abc.supabase.co", "s").isFailure)
        assertTrue(Assistente.montarBanco(pooler, null).isFailure)
    }

    @Test fun testaTelegramEGeminiEMontaOBloco() = runBlocking {
        val srv = MockWebServer().apply { start() }
        val base = srv.url("/").toString().trimEnd('/')
        val mem = mutableMapOf<String, String>()
        val a = Assistente(object : bot.afiliados.data.Segredos {
            override fun salvar(nome: String, valor: String?) { if (valor == null) mem.remove(nome) else mem[nome] = valor }
            override fun ler(nome: String) = mem[nome]
        }, OkHttpClient(), base, base)
        val token = "123456789:" + "A".repeat(35)
        assertTrue(a.testarTelegram("abc").isFailure)
        srv.enqueue(MockResponse().setBody("""{"ok":true,"result":{"username":"achadinhos_bot"}}"""))
        assertEquals("@achadinhos_bot", a.testarTelegram(token).getOrThrow())
        assertEquals("/bot$token/getMe", srv.takeRequest().path)
        srv.enqueue(MockResponse().setResponseCode(401).setBody("""{"ok":false}"""))
        assertTrue(a.testarTelegram(token).isFailure)
        srv.enqueue(MockResponse().setResponseCode(400).setBody("{}"))
        assertTrue(a.testarGemini("AIza" + "x".repeat(35)).isFailure)
        srv.enqueue(MockResponse().setBody("""{"models":[]}"""))
        assertTrue(a.testarGemini("AIza" + "x".repeat(35)).isSuccess)
        srv.takeRequest(); val g = srv.takeRequest()
        assertEquals("AIza" + "x".repeat(35), g.getHeader("x-goog-api-key"))
        a.telegram = token; a.gemini = "AIzaK"; a.banco = "postgresql://b"
        val bloco = a.blocoEnv()
        assertTrue(bloco.startsWith("APP_TOKEN=") && bloco.contains("TELEGRAM_BOT_TOKEN=$token") && bloco.contains("DATABASE_URL=postgresql://b"))
        // O token do app é sempre o mesmo depois de gerado.
        assertEquals(a.appToken, a.appToken)
        srv.shutdown()
    }
}
