package bot.afiliados

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Abre o app e passa por todas as abas: nada pode travar com o banco vazio. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TelasTest {
    val regra = createAndroidComposeRule<MainActivity>()
    // Pula a configuração guiada antes de abrir o app.
    @get:Rule val cadeia: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(object : org.junit.rules.ExternalResource() {
        override fun before() {
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
                .getSharedPreferences("preferencias", 0).edit().putBoolean("assistenteVisto", pularAssistente).commit()
        }
    }).around(regra)
    val pularAssistente = true

    @Test fun navegaPorTodasAsAbasSemDados() {
        regra.onNodeWithText("Afiliados.bot").assertExists()
        regra.onNodeWithText("Servidor não configurado", substring = true).assertExists()

        regra.onAllNodesWithText("Produtos").onFirst().performClick()
        regra.onNodeWithText("Catálogo vazio").assertExists()

        regra.onAllNodesWithText("Nichos").onFirst().performClick()
        regra.waitUntil(5_000) { regra.onAllNodesWithText("Tecnologia e eletrônicos").fetchSemanticsNodes().isNotEmpty() }

        regra.onAllNodesWithText("Fila").onFirst().performClick()
        regra.onNodeWithText("Fila vazia").assertExists()

        regra.onAllNodesWithText("Ajustes").onFirst().performClick()
        regra.onNode(hasScrollAction()).performScrollToNode(hasText("Backend"))
        regra.onNodeWithText("Backend").assertExists()
        regra.onNode(hasScrollAction()).performScrollToNode(hasText("Abrir comissões"))
        regra.onNodeWithText("Abrir comissões").performClick()
        regra.onNodeWithText("Nenhuma comissão registrada").assertExists()
    }

    @Test fun cadastroManualPorLink() {
        regra.onNodeWithText("Novo produto").performClick()
        regra.onNodeWithText("Preencher à mão").assertExists()
        regra.onNodeWithText("Analisar").assertExists()
    }
}

/** Primeira abertura: a configuração guiada aparece sozinha. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssistenteTelaTest {
    val regra = createAndroidComposeRule<MainActivity>()
    @get:Rule val cadeia: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(object : org.junit.rules.ExternalResource() {
        override fun before() {
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
                .getSharedPreferences("preferencias", 0).edit().putBoolean("assistenteVisto", false).commit()
        }
    }).around(regra)

    @Test fun abreSozinhoEAvancaAteOTelegram() {
        regra.waitUntil(5_000) { regra.onAllNodesWithText("Vamos deixar tudo pronto").fetchSemanticsNodes().isNotEmpty() }
        regra.onNodeWithText("Começar").performClick()
        regra.onNodeWithText("Crie o robô do Telegram").assertExists()
        regra.onNodeWithText("Abrir o BotFather no Telegram").assertExists()
    }
}
