package bot.afiliados.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Prepara um print para a IA: reduz para no máximo 1568 px e envia como JPEG. */
object Imagens {
    private const val LADO_MAX = 1568

    fun paraPrint(cr: ContentResolver, uri: Uri): ImagemPrint {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) } ?: error("Não deu para abrir a imagem")
        if (limites.outWidth <= 0 || limites.outHeight <= 0) error("Arquivo não é uma imagem")
        var amostra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (amostra * 2) >= LADO_MAX) amostra *= 2
        val bruto = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra }) }
            ?: error("Não deu para ler a imagem")
        val escala = LADO_MAX.toFloat() / maxOf(bruto.width, bruto.height)
        val bmp = if (escala < 1f) Bitmap.createScaledBitmap(bruto, (bruto.width * escala).toInt(), (bruto.height * escala).toInt(), true) else bruto
        val saida = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, saida)
        return ImagemPrint("image/jpeg", Base64.encodeToString(saida.toByteArray(), Base64.NO_WRAP))
    }
}
