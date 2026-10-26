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
        // Com inJustDecodeBounds o decode sempre devolve null: só lemos as medidas.
        val entrada = cr.openInputStream(uri) ?: error("Não deu para abrir a imagem")
        entrada.use { BitmapFactory.decodeStream(it, null, limites) }
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

    /** Foto já salva no aparelho, pronta para ir ao Telegram. */
    fun paraEnvio(caminho: String): ImagemPrint? {
        val arq = java.io.File(caminho)
        if (!arq.isFile) return null
        return ImagemPrint("image/jpeg", Base64.encodeToString(arq.readBytes(), Base64.NO_WRAP))
    }
}

/** Fotos dos produtos ficam em files/fotos (fora do backup do Android). */
object Fotos {
    private const val LADO_FOTO = 1280

    private fun pasta(ctx: android.content.Context) = java.io.File(ctx.filesDir, "fotos").apply { mkdirs() }

    private fun abrir(cr: ContentResolver, uri: Uri, ladoMax: Int): Bitmap {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (cr.openInputStream(uri) ?: error("Não deu para abrir a imagem")).use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0) error("Arquivo não é uma imagem")
        var amostra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (amostra * 2) >= ladoMax) amostra *= 2
        return (cr.openInputStream(uri) ?: error("Não deu para abrir a imagem")).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra })
        } ?: error("Não deu para ler a imagem")
    }

    private fun salvar(ctx: android.content.Context, bmp: Bitmap): String {
        val escala = LADO_FOTO.toFloat() / maxOf(bmp.width, bmp.height)
        val final = if (escala < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * escala).toInt(), (bmp.height * escala).toInt(), true) else bmp
        val arq = java.io.File(pasta(ctx), java.util.UUID.randomUUID().toString() + ".jpg")
        arq.outputStream().use { final.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return arq.absolutePath
    }

    /** Recorta a foto do produto do print. caixa = [ymin, xmin, ymax, xmax] de 0 a 1000. */
    fun recortar(ctx: android.content.Context, uri: Uri, caixa: List<Double>): String {
        require(caixa.size == 4) { "Recorte inválido" }
        val bmp = abrir(ctx.contentResolver, uri, 2048)
        val (y0, x0, y1, x1) = caixa.map { it.coerceIn(0.0, 1000.0) / 1000.0 }
        val l = (x0 * bmp.width).toInt(); val t = (y0 * bmp.height).toInt()
        val w = ((x1 - x0) * bmp.width).toInt().coerceAtMost(bmp.width - l)
        val h = ((y1 - y0) * bmp.height).toInt().coerceAtMost(bmp.height - t)
        require(w > 50 && h > 50) { "Recorte pequeno demais" }
        return salvar(ctx, Bitmap.createBitmap(bmp, l, t, w, h))
    }

    /** Usa uma imagem da galeria como foto do produto. */
    fun daGaleria(ctx: android.content.Context, uri: Uri): String = salvar(ctx, abrir(ctx.contentResolver, uri, 2048))
}
