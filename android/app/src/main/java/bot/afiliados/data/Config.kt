package bot.afiliados.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda o token do backend cifrado com uma chave do Android Keystore
 * (a chave nunca sai do hardware/sistema). O token não está no APK.
 */
class Cofre(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("cofre", Context.MODE_PRIVATE)
    private val alias = "afiliados_token_v1"

    private fun chave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun salvar(nome: String, valor: String?) {
        if (valor.isNullOrEmpty()) {
            prefs.edit().remove(nome).apply()
            return
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, chave()) }
        val dados = c.iv + c.doFinal(valor.toByteArray())
        prefs.edit().putString(nome, Base64.encodeToString(dados, Base64.NO_WRAP)).apply()
    }

    fun ler(nome: String): String? {
        val b64 = prefs.getString(nome, null) ?: return null
        return try {
            val dados = Base64.decode(b64, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(128, dados, 0, 12))
            String(c.doFinal(dados, 12, dados.size - 12))
        } catch (_: Exception) {
            null
        }
    }
}

/** Preferências que não são segredo. */
class Preferencias(ctx: Context) {
    private val p = ctx.getSharedPreferences("preferencias", Context.MODE_PRIVATE)

    var backendUrl: String
        get() = p.getString("backendUrl", "") ?: ""
        set(v) = p.edit().putString("backendUrl", v.trim().trimEnd('/')).apply()

    var validadePrecoHoras: Int
        get() = p.getInt("validadePrecoHoras", 6)
        set(v) = p.edit().putInt("validadePrecoHoras", v.coerceIn(1, 168)).apply()

    /** "sistema", "preto" ou "branco". */
    var tema: String
        get() = p.getString("tema", "sistema") ?: "sistema"
        set(v) = p.edit().putString("tema", v).apply()

    /** Modelo das mensagens: {titulo} {de} {por} {link} {cupom}. */
    var modeloMensagem: String
        get() = p.getString("modeloMensagem", null) ?: bot.afiliados.domain.Mensagem.MODELO_PADRAO
        set(v) = p.edit().putString("modeloMensagem", v).apply()

    /** Cupom usado quando o produto não tem um próprio. */
    var cupomPadrao: String
        get() = p.getString("cupomPadrao", "") ?: ""
        set(v) = p.edit().putString("cupomPadrao", v.trim().uppercase().filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(30)).apply()

    /** Minutos entre uma publicação e outra no "Publicar todos". */
    var intervaloMinutos: Int
        get() = p.getInt("intervaloMinutos", 15)
        set(v) = p.edit().putInt("intervaloMinutos", v.coerceIn(1, 240)).apply()

    var bloqueioLocal: Boolean
        get() = p.getBoolean("bloqueioLocal", false)
        set(v) = p.edit().putBoolean("bloqueioLocal", v).apply()

    var subIdPadrao: String
        get() = p.getString("subIdPadrao", "afiliadosbot") ?: "afiliadosbot"
        set(v) = p.edit().putString("subIdPadrao", v.filter { it.isLetterOrDigit() }.take(50)).apply()
}
