package bot.afiliados

import bot.afiliados.domain.EstadoFila
import bot.afiliados.domain.Fila
import bot.afiliados.domain.Links
import bot.afiliados.domain.Marketplace
import bot.afiliados.domain.Mensagem
import bot.afiliados.domain.Precos
import bot.afiliados.domain.SituacaoPreco
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    @Test fun aceitaLojasConhecidasEmHttps() {
        val r = Links.validar("https://shopee.com.br/Fone-i.1.2")
        assertTrue(r is Links.Resultado.Ok && r.marketplace == Marketplace.SHOPEE)
        val m = Links.validar("https://produto.mercadolivre.com.br/MLB-123456789-x")
        assertTrue(m is Links.Resultado.Ok && m.marketplace == Marketplace.MERCADO_LIVRE)
    }

    @Test fun recusaLinksPerigosos() {
        assertTrue(Links.validar("http://shopee.com.br/x") is Links.Resultado.Erro)
        assertTrue(Links.validar("https://shopee.com.br.golpe.xyz/x") is Links.Resultado.Erro)
        assertTrue(Links.validar("https://a:b@shopee.com.br/x") is Links.Resultado.Erro)
        assertTrue(Links.validar("https://shopee.com.br:8080/x") is Links.Resultado.Erro)
        assertTrue(Links.validar("javascript:alert(1)") is Links.Resultado.Erro)
        assertTrue(Links.validar("") is Links.Resultado.Erro)
    }

    @Test fun lojaDesconhecidaSoComoOutro() {
        assertTrue(Links.validar("https://loja.exemplo.com/p", exigirConhecido = true) is Links.Resultado.Erro)
        val r = Links.validar("https://loja.exemplo.com/p", exigirConhecido = false)
        assertTrue(r is Links.Resultado.Ok && r.marketplace == Marketplace.OUTRO)
    }

    @Test fun chaveIgnoraRastreioParaDetectarRepetido() {
        assertEquals("shopee:11:22", Links.chave("https://shopee.com.br/Fone-i.11.22?sp_atk=abc&xptdk=1"))
        assertEquals("shopee:11:22", Links.chave("https://shopee.com.br/product/11/22"))
        assertEquals("ml:MLB123456789", Links.chave("https://produto.mercadolivre.com.br/MLB-123456789-fone-_JM?matt_tool=1"))
        assertEquals("loja.com/p/abc", Links.chave("https://LOJA.com/p/abc/?utm=1"))
    }

    @Test fun extraiLinkDeTextoCompartilhado() {
        assertEquals("https://s.shopee.com.br/abc", Links.extrairDeTexto("Olha que barato! https://s.shopee.com.br/abc."))
        assertNull(Links.extrairDeTexto("sem link"))
    }
}

class PrecosTest {
    @Test fun descontoSoComPrecoAnteriorComprovado() {
        assertEquals(40, Precos.desconto(59.9, 99.9, true))
        assertNull(Precos.desconto(59.9, 99.9, false))
        assertNull(Precos.desconto(59.9, null, true))
        assertNull(Precos.desconto(99.9, 59.9, true))
    }

    @Test fun comissaoEstimada() {
        assertEquals(4.79, Precos.comissaoEstimada(59.9, 8.0)!!, 0.001)
        assertNull(Precos.comissaoEstimada(null, 8.0))
    }

    @Test fun precoVenceDepoisDaValidade() {
        val agora = 100L * 3_600_000
        assertEquals(SituacaoPreco.VERIFICADO, Precos.situacaoAtual(SituacaoPreco.VERIFICADO, agora - 3_600_000, 6, agora))
        assertEquals(SituacaoPreco.POSSIVELMENTE_DESATUALIZADO, Precos.situacaoAtual(SituacaoPreco.VERIFICADO, agora - 7 * 3_600_000, 6, agora))
        assertEquals(SituacaoPreco.POSSIVELMENTE_DESATUALIZADO, Precos.situacaoAtual(SituacaoPreco.VERIFICADO_MANUAL, agora - 7 * 3_600_000, 6, agora))
        assertEquals(SituacaoPreco.NUNCA_VERIFICADO, Precos.situacaoAtual(SituacaoPreco.VERIFICADO, null, 6, agora))
        assertEquals(SituacaoPreco.VERIFICACAO_INDISPONIVEL, Precos.situacaoAtual(SituacaoPreco.VERIFICACAO_INDISPONIVEL, agora, 6, agora))
    }

    @Test fun leValoresEmFormatoBrasileiro() {
        assertEquals(1299.9, Precos.ler("1.299,90")!!, 0.001)
        assertEquals(59.9, Precos.ler("R$ 59,90")!!, 0.001)
        assertEquals(59.9, Precos.ler("59.9")!!, 0.001)
        assertNull(Precos.ler("abc"))
        assertNull(Precos.ler("-3"))
    }
}

class FilaTest {
    @Test fun naoPublicaSemConfirmacao() {
        assertTrue(Fila.pode(EstadoFila.EM_PROCESSAMENTO, EstadoFila.PUBLICADO_CONFIRMADO))
        assertFalse(Fila.pode(EstadoFila.AGENDADO, EstadoFila.PUBLICADO_CONFIRMADO))
        assertFalse(Fila.pode(EstadoFila.PRONTO, EstadoFila.PUBLICADO_CONFIRMADO))
        assertFalse(Fila.pode(EstadoFila.RASCUNHO, EstadoFila.AGENDADO))
    }

    @Test fun estadosFinaisNaoMudam() {
        listOf(EstadoFila.PUBLICADO_CONFIRMADO, EstadoFila.PUBLICADO_INFORMADO, EstadoFila.CANCELADO).forEach { f ->
            EstadoFila.entries.forEach { assertFalse("$f → $it", Fila.pode(f, it)) }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun transicaoInvalidaFalha() {
        Fila.transicao(EstadoFila.PUBLICADO_CONFIRMADO, EstadoFila.PRONTO)
    }

    @Test fun envioNaoConfirmadoSoPermiteRetentarConfirmarOuCancelar() {
        assertTrue(Fila.pode(EstadoFila.ENVIO_NAO_CONFIRMADO, EstadoFila.EM_PROCESSAMENTO))
        assertTrue(Fila.pode(EstadoFila.ENVIO_NAO_CONFIRMADO, EstadoFila.PUBLICADO_INFORMADO))
        assertFalse(Fila.pode(EstadoFila.ENVIO_NAO_CONFIRMADO, EstadoFila.PUBLICADO_CONFIRMADO))
        assertFalse(Fila.pode(EstadoFila.ENVIO_NAO_CONFIRMADO, EstadoFila.PRONTO))
    }

    @Test fun precoVencidoBloqueiaPublicacao() {
        val b = Fila.bloqueios("Oferta", "https://s.shopee.com.br/x", SituacaoPreco.POSSIVELMENTE_DESATUALIZADO, precoConfirmadoAgora = false)
        assertTrue(b.any { it.startsWith("Preço") })
        assertTrue(Fila.bloqueios("Oferta", "https://s.shopee.com.br/x", SituacaoPreco.VERIFICADO, false).isEmpty())
        assertTrue(Fila.bloqueios("", null, SituacaoPreco.VERIFICADO, false).size == 2)
        assertTrue(Fila.bloqueios("x", "http://a.com", SituacaoPreco.VERIFICADO, false).isNotEmpty())
    }
}

class MensagemTest {
    @Test fun linkVaiNoFimSemAlteracao() {
        val link = "https://s.shopee.com.br/AbC?x=1"
        val m = Mensagem.montar("  Fone por R$ 59,90  ", link, listOf("#fone", ""))
        assertTrue(m.endsWith(link))
        assertEquals("Fone por R$ 59,90\n\n#fone\n\n$link", m)
    }
}
