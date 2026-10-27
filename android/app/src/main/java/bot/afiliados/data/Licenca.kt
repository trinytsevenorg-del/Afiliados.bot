package bot.afiliados.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import java.util.concurrent.TimeUnit

/** O que vai dentro do token assinado pelo servidor central. */
@Serializable data class DadosLicenca(
    /** Código da licença. */ val c: String,
    /** Plano: essencial, pro ou premium. */ val p: String,
    /** Expira em (ms). null = para sempre. */ val e: Long? = null,
    /** Atualizações até (ms). */ val u: Long,
    /** Aparelho. */ val a: String,
    /** Verificado em (ms). */ val v: Long,
)

@Serializable data class Ativacao(
    val token: String, val codigo: String, val plano: String, val nomePlano: String, val email: String = "",
    val expira: Long? = null, val atualizacoesAte: Long,
)

@Serializable data class VersaoApp(val versionCode: Int, val versao: String, val data: Long, val novidades: List<String> = emptyList(), val url: String = "/baixar")

sealed interface SituacaoLicenca {
    data object SemLicenca : SituacaoLicenca
    data class Ativa(val dados: DadosLicenca) : SituacaoLicenca
    data class Vencida(val dados: DadosLicenca) : SituacaoLicenca
    /** APK reassinado por outra pessoa (clone ou modificado). */
    data object Adulterado : SituacaoLicenca
    /** O app instalado saiu depois do fim das atualizações do plano. */
    data class VersaoForaDoPlano(val dados: DadosLicenca) : SituacaoLicenca
}

val NOMES_PLANOS = mapOf("essencial" to "Essencial", "pro" to "Pro", "premium" to "Premium")

/**
 * Confere a licença sem internet: assinatura ECDSA com a chave pública que vem no app,
 * o aparelho, a validade e se esta versão do app está dentro do plano.
 */
class ConferidorLicenca(chavePublicaBase64: String, private val dataVersao: Long) {
    private val chave = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(chavePublicaBase64, Base64.NO_WRAP)))
    private val json = Json { ignoreUnknownKeys = true }

    fun ler(token: String?): DadosLicenca? {
        val partes = token?.split('.') ?: return null
        if (partes.size != 2) return null
        return try {
            val dados = Base64.decode(partes[0], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val assinatura = Base64.decode(partes[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val ok = Signature.getInstance("SHA256withECDSA").run { initVerify(chave); update(dados); verify(assinatura) }
            if (ok) json.decodeFromString(DadosLicenca.serializer(), String(dados)) else null
        } catch (_: Exception) {
            null
        }
    }

    fun situacao(token: String?, aparelho: String, agora: Long): SituacaoLicenca {
        val d = ler(token) ?: return SituacaoLicenca.SemLicenca
        if (d.a != aparelho) return SituacaoLicenca.SemLicenca
        if (d.e != null && agora > d.e) return SituacaoLicenca.Vencida(d)
        if (dataVersao > d.u) return SituacaoLicenca.VersaoForaDoPlano(d)
        return SituacaoLicenca.Ativa(d)
    }
}

class FalhaLicenca(val status: Int, msg: String) : Exception(msg)

/** Guarda a licença e fala com o servidor central (sem o token do backend do cliente). */
class Licencas(
    ctx: Context,
    private val central: String,
    private val conferidor: ConferidorLicenca,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(70, TimeUnit.SECONDS).build(),
    private val agora: () -> Long = System::currentTimeMillis,
) {
    private val prefs = ctx.getSharedPreferences("licenca", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Id deste aparelho para a licença (aleatório, não é o IMEI). */
    val aparelho: String
        get() = prefs.getString("aparelho", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("aparelho", it).apply() }

    var token: String?
        get() = prefs.getString("token", null)
        private set(v) { prefs.edit().putString("token", v).apply() }

    fun situacao() = conferidor.situacao(token, aparelho, agora())
    fun dados() = conferidor.ler(token)
    fun sair() { prefs.edit().remove("token").apply() }

    @Serializable private data class Pedido(val codigo: String? = null, val email: String? = null, val pedido: String? = null, val aparelho: String)
    @Serializable private data class Erro(val mensagem: String = "")

    suspend fun ativar(codigo: String?, email: String?, pedido: String?): Ativacao = withContext(Dispatchers.IO) {
        val corpo = json.encodeToString(Pedido.serializer(), Pedido(codigo?.trim()?.ifBlank { null }, email?.trim()?.ifBlank { null }, pedido?.trim()?.ifBlank { null }, aparelho))
        val req = Request.Builder().url("$central/api/licenca/ativar").post(corpo.toRequestBody("application/json".toMediaType())).build()
        val (status, texto) = try {
            http.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: java.io.IOException) {
            throw FalhaLicenca(0, "Sem conexão com o servidor de licenças. Tente de novo.")
        }
        if (status !in 200..299) {
            throw FalhaLicenca(status, runCatching { json.decodeFromString(Erro.serializer(), texto).mensagem }.getOrNull()?.ifBlank { null } ?: "Erro $status")
        }
        val a = json.decodeFromString(Ativacao.serializer(), texto)
        if (conferidor.ler(a.token) == null) throw FalhaLicenca(500, "Resposta do servidor de licenças não confere")
        token = a.token
        a
    }

    /**
     * Uma vez por dia, renova o token (assinatura mensal, reembolso, celular trocado).
     * Sem internet, a licença salva continua valendo até a data dela.
     */
    suspend fun renovarSePrecisar(): SituacaoLicenca {
        val d = dados() ?: return situacao()
        if (agora() - d.v < 24 * 3_600_000L) return situacao()
        try {
            ativar(d.c, null, null)
        } catch (e: FalhaLicenca) {
            // Desativada (reembolso) ou vencida no servidor: some daqui também.
            if (e.status == 403 || e.status == 402 || e.status == 404) sair()
        }
        return situacao()
    }

    suspend fun versaoNova(): VersaoApp? = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url("$central/api/app/versao").build()).execute().use { r ->
                if (!r.isSuccessful) null else json.decodeFromString(VersaoApp.serializer(), r.body?.string().orEmpty())
            }
        } catch (_: Exception) {
            null
        }
    }

    fun linkBaixar(ate: Long? = null) = "$central/baixar" + (ate?.let { "?ate=$it" } ?: "")
    fun linkSite() = "$central/site"
}

/** Confere se o APK foi assinado com a chave do dono. Clone reassinado tem outra impressão digital. */
object Integridade {
    fun assinatura(ctx: Context): String? = try {
        val pm = ctx.packageManager
        val certs = if (android.os.Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures
        }
        certs?.firstOrNull()?.toByteArray()?.let { b ->
            java.security.MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02X".format(it) }
        }
    } catch (_: Exception) {
        null
    }
}
