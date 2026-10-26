package bot.afiliados

import bot.afiliados.domain.Mensagem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeloTest {
    private val m = Mensagem.MODELO_PADRAO

    @Test fun modeloCompletoIgualAoDoPrint() {
        val t = Mensagem.aplicarModelo(m, "Rider R1 Brasil: conforto e estilo!", 32.0, 21.0, "OFERTANETS")
        val final = Mensagem.montar(t, "https://meli.la/abc")
        assertEquals(
            """
            💥 Rider R1 Brasil: conforto e estilo!

            De: ~R$ 32,00~ ❌
            💸 Por: *R$ 21,00* 🔥

            🔗 Compre aqui: https://meli.la/abc

            🎟️ Use o cupom: *OFERTANETS*

            ⚠️ Promoção sujeita a alteração a qualquer momento.
            """.trimIndent(),
            final,
        )
    }

    @Test fun semPrecoAnteriorESemCupomAsLinhasSomem() {
        val t = Mensagem.aplicarModelo(m, "Torneira Gourmet", null, 34.94, null)
        assertFalse(t.contains("De:"))
        assertFalse(t.contains("cupom"))
        assertFalse(t.contains("\n\n\n"))
        assertTrue(t.contains("*R$ 34,94*"))
        assertTrue(t.contains(Mensagem.LINK))
    }

    @Test fun telegramRecebeHtmlSeguro() {
        val html = Mensagem.paraTelegramHtml("De: ~R$ 32,00~ ❌\nPor: *R$ 21,00* <promo> & mais\nhttps://meli.la/a_b_c")
        assertEquals("De: <s>R$ 32,00</s> ❌\nPor: <b>R$ 21,00</b> &lt;promo&gt; &amp; mais\nhttps://meli.la/a_b_c", html)
    }
}
