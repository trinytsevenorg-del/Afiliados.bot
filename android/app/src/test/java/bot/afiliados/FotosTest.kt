package bot.afiliados

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import bot.afiliados.data.BancoAfiliados
import bot.afiliados.data.Fotos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FotosTest {
    @get:Rule val migracoes = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BancoAfiliados::class.java)

    @Test fun recortaSoAFotoDoPrint() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        // Print de 1000x2000: metade de cima vermelha (a "foto"), resto cinza.
        val bmp = Bitmap.createBitmap(1000, 2000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        android.graphics.Canvas(bmp).drawRect(0f, 0f, 1000f, 1000f, android.graphics.Paint().apply { color = Color.RED })
        val arq = File(ctx.cacheDir, "print.png").apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val caminho = Fotos.recortar(ctx, Uri.fromFile(arq), listOf(0.0, 0.0, 500.0, 1000.0))
        val foto = BitmapFactory.decodeFile(caminho)
        assertEquals(foto.width, foto.height)
        val meio = foto.getPixel(foto.width / 2, foto.height / 2)
        assertTrue("centro deveria ser vermelho", Color.red(meio) > 200 && Color.green(meio) < 60)
        assertTrue(caminho.contains("/fotos/"))
    }

    @Test fun bancoAntigoGanhaAColunaDaFotoSemPerderProdutos() {
        migracoes.createDatabase("migracao.db", 1).use { db ->
            db.execSQL("INSERT INTO produtos (nome, linkOriginal, chave, marketplace, precoAnteriorComprovado, descricaoOriginal, caracteristicas, observacoes, situacaoLink, situacaoPreco, arquivado, criadoEm, atualizadoEm) VALUES ('Fone','https://a.com','k','OUTRO',0,'','','','NAO_VERIFICADO','NUNCA_VERIFICADO',0,0,0)")
        }
        val db = migracoes.runMigrationsAndValidate("migracao.db", 5, true, BancoAfiliados.MIGRACAO_1_2, BancoAfiliados.MIGRACAO_2_3, BancoAfiliados.MIGRACAO_3_4, BancoAfiliados.MIGRACAO_4_5)
        db.query("SELECT nome, fotoLocal, cupom FROM produtos").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Fone", c.getString(0))
            assertTrue(c.isNull(1))
            assertTrue(c.isNull(2))
        }
    }
}
