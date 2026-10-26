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
    @get:Rule val regra = createAndroidComposeRule<MainActivity>()

    @Test fun navegaPorTodasAsAbasSemDados() {
        regra.onNodeWithText("Afiliados.bot").assertExists()
        regra.onNodeWithText("Backend não configurado", substring = true).assertExists()

        regra.onAllNodesWithText("Produtos").onFirst().performClick()
        regra.onNodeWithText("Catálogo vazio").assertExists()

        regra.onAllNodesWithText("Nichos").onFirst().performClick()
        regra.waitUntil(5_000) { regra.onAllNodesWithText("Tecnologia e eletrônicos").fetchSemanticsNodes().isNotEmpty() }

        regra.onAllNodesWithText("Fila").onFirst().performClick()
        regra.onNodeWithText("Fila vazia").assertExists()

        regra.onAllNodesWithText("Ajustes").onFirst().performClick()
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
