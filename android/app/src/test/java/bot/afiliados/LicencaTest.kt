package bot.afiliados

import androidx.test.core.app.ApplicationProvider
import bot.afiliados.data.ConferidorLicenca
import bot.afiliados.data.FalhaLicenca
import bot.afiliados.data.Licencas
import bot.afiliados.data.SituacaoLicenca
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LicencaTest {
    // Par de chaves descartável: token assinado pelo Node (crypto.sign sha256, P-256), igual ao servidor central.
    private val publica = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEa/Tqi+M+puCsPuPW4CNZNSzxMJyAPLIVz45r4GowMpq4DH+EOnetCbYG/2QiPN5rpljEBFiR5mc77OH0YHGKHA=="
    private val token = "eyJjIjoiQUJPVC1URVNULVRFU1QtVEVTVCIsInAiOiJwcm8iLCJlIjpudWxsLCJ1IjoxOTAwMDAwMDAwMDAwLCJhIjoiYXBhcmVsaG8teCIsInYiOjE4MDAwMDAwMDAwMDB9.MEYCIQDXWgQG46Y2TOSfnGqdbPXrceUicAMvhrCmErkirnpbggIhANX3tdvn8Q7xwTy9MY8e9YCKRU0tSmVI9mAqzX4GTNpg"

    @Test fun confereAssinaturaDoServidorAparelhoEVersao() {
        val c = ConferidorLicenca(publica, dataVersao = 1_850_000_000_000)
        assertEquals("pro", c.ler(token)!!.p)
        assertTrue(c.situacao(token, "aparelho-x", 1_800_000_000_000) is SituacaoLicenca.Ativa)
        // Outro celular, token mexido ou sem token: sem licença.
        assertTrue(c.situacao(token, "outro", 0) is SituacaoLicenca.SemLicenca)
        val falso = android.util.Base64.encodeToString("""{"c":"ABOT-TEST-TEST-TEST","p":"premium","e":null,"u":9900000000000,"a":"aparelho-x","v":1}""".toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING) + "." + token.substringAfter('.')
        assertNull(c.ler(falso))
        assertTrue(c.situacao(null, "aparelho-x", 0) is SituacaoLicenca.SemLicenca)
        // App mais novo que o fim das atualizações do plano.
        val nova = ConferidorLicenca(publica, dataVersao = 1_950_000_000_000)
        assertTrue(nova.situacao(token, "aparelho-x", 0) is SituacaoLicenca.VersaoForaDoPlano)
    }

    @Test fun ativaPeloServidorEGuardaOToken() = runBlocking {
        val srv = MockWebServer().apply { start() }
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val l = Licencas(ctx, srv.url("/").toString().trimEnd('/'), ConferidorLicenca(publica, 1_850_000_000_000), OkHttpClient()) { 1_800_000_000_000 }
        ctx.getSharedPreferences("licenca", 0).edit().putString("aparelho", "aparelho-x").commit()
        srv.enqueue(MockResponse().setResponseCode(404).setBody("""{"erro":"nao_encontrado","mensagem":"Licença não encontrada."}"""))
        try { l.ativar(null, "a@b.com", "K1"); fail() } catch (e: FalhaLicenca) { assertEquals("Licença não encontrada.", e.message) }
        srv.enqueue(MockResponse().setBody("""{"token":"$token","codigo":"ABOT-TEST-TEST-TEST","plano":"pro","nomePlano":"Pro","atualizacoesAte":1900000000000}"""))
        assertEquals("Pro", l.ativar(null, "a@b.com", "K1").nomePlano)
        assertTrue(srv.takeRequest().body.readUtf8().contains("\"aparelho\":\"aparelho-x\""))
        assertTrue(l.situacao() is SituacaoLicenca.Ativa)
        // Resposta com token falso não é aceita.
        srv.enqueue(MockResponse().setBody("""{"token":"x.y","codigo":"C","plano":"pro","nomePlano":"Pro","atualizacoesAte":1}"""))
        try { l.ativar("C", null, null); fail() } catch (e: FalhaLicenca) { assertEquals(500, e.status) }
        srv.shutdown()
    }
}
