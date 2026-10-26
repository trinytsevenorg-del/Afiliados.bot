package bot.afiliados

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import bot.afiliados.data.Imagens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Um print de celular de verdade (1170×2532) precisa abrir e ser reduzido. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImagensTest {
    @Test fun printDeCelularAbreEDiminui() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val bmp = Bitmap.createBitmap(1170, 2532, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        val arq = File(ctx.cacheDir, "print.png").apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val img = Imagens.paraPrint(ctx.contentResolver, Uri.fromFile(arq))
        assertEquals("image/jpeg", img.tipo)
        val bytes = Base64.decode(img.base64, Base64.NO_WRAP)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        assertTrue("lado maior ${o.outHeight}", o.outHeight in 1000..1568)
    }

    @Test(expected = IllegalStateException::class)
    fun arquivoQueNaoEImagemFalhaComMensagem() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val arq = File(ctx.cacheDir, "nada.txt").apply { writeText("não sou imagem") }
        Imagens.paraPrint(ctx.contentResolver, Uri.fromFile(arq))
    }
}
