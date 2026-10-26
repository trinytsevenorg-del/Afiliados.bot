package bot.afiliados

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Gera prints das telas. Só roda com -Dprints=<pasta>. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-night-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrintsTest {
    @get:Rule val regra = createAndroidComposeRule<MainActivity>()

    @Test fun prints() {
        val pasta = System.getProperty("prints").orEmpty()
        assumeTrue(pasta.isNotBlank())
        fun salvar(nome: String) {
            regra.waitForIdle()
            regra.activityRule.scenario.onActivity { a ->
                val v = a.window.decorView
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp))
                File(pasta, "$nome.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        salvar("1-inicio")
        listOf("Produtos" to "2-produtos", "Nichos" to "3-nichos", "Fila" to "4-fila", "Ajustes" to "5-ajustes").forEach { (aba, nome) ->
            regra.onAllNodesWithText(aba).onFirst().performClick()
            salvar(nome)
        }
    }
}
