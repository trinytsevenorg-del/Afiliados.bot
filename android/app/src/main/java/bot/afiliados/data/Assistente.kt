package bot.afiliados.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Configuração guiada: confere cada chave na hora (sem esperar o servidor subir)
 * e monta o bloco para colar no Render ("Add from .env").
 */
class Assistente(
    private val cofre: Segredos,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
    private val telegramBase: String = "https://api.telegram.org",
    private val geminiBase: String = "https://generativelanguage.googleapis.com",
) {
    companion object {
        /** Imagem pronta do servidor (pública). O cliente não precisa de GitHub. */
        const val IMAGEM = "ghcr.io/trinytsevenorg-del/afiliados-bot:latest"
        private const val ALFA = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
        fun gerar(n: Int): String { val r = SecureRandom(); return (1..n).map { ALFA[r.nextInt(ALFA.length)] }.joinToString("") }

        /** Endereço do Supabase: troca [YOUR-PASSWORD] pela senha gerada e recusa o "Direct". */
        fun montarBanco(colado: String, senha: String?): Result<String> {
            var u = colado.trim().removePrefix("DATABASE_URL=").trim('"', '\'', ' ')
            if (!u.startsWith("postgres")) return Result.failure(IllegalArgumentException("Cole o endereço que começa com postgresql://"))
            if (u.contains("[YOUR-PASSWORD]") || u.contains("YOUR-PASSWORD")) {
                if (senha.isNullOrBlank()) return Result.failure(IllegalArgumentException("Troque [YOUR-PASSWORD] pela senha do banco"))
                u = u.replace("[YOUR-PASSWORD]", senha).replace("YOUR-PASSWORD", senha)
            }
            if (Regex("@db\\.[a-z0-9]+\\.supabase\\.co").containsMatchIn(u)) {
                return Result.failure(IllegalArgumentException("Esse é o endereço Direct. Escolha Session pooler (Método de conexão)"))
            }
            if (!u.contains("pooler.supabase.com")) return Result.failure(IllegalArgumentException("Use o endereço do Session pooler (termina em pooler.supabase.com:5432/postgres)"))
            return Result.success(u)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    var telegram: String? get() = cofre.ler("as_telegram"); set(v) = cofre.salvar("as_telegram", v)
    var gemini: String? get() = cofre.ler("as_gemini"); set(v) = cofre.salvar("as_gemini", v)
    var banco: String? get() = cofre.ler("as_banco"); set(v) = cofre.salvar("as_banco", v)
    var senhaBanco: String? get() = cofre.ler("as_senha"); set(v) = cofre.salvar("as_senha", v)

    /** Token que o app e o servidor do cliente combinam (gerado aqui, uma vez). */
    val appToken: String
        get() = cofre.ler("as_app_token") ?: gerar(48).also { cofre.salvar("as_app_token", it) }

    /** Confere o token do bot. Devolve o @ do bot. */
    suspend fun testarTelegram(token: String): Result<String> = withContext(Dispatchers.IO) {
        val t = token.trim()
        if (!Regex("^\\d{5,12}:[A-Za-z0-9_-]{30,}$").matches(t)) return@withContext Result.failure(IllegalArgumentException("O token parece incompleto. Copie a linha inteira do BotFather (números:letras)."))
        runCatching {
            http.newCall(Request.Builder().url("$telegramBase/bot$t/getMe").build()).execute().use { r ->
                val j = json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
                if (!r.isSuccessful || j["ok"]?.jsonPrimitive?.boolean != true) error("O Telegram recusou esse token. Copie de novo no BotFather.")
                "@" + j["result"]!!.jsonObject["username"]!!.jsonPrimitive.content
            }
        }.recoverCatching { e -> throw IllegalArgumentException(if (e is java.io.IOException) "Sem internet para testar. Tente de novo." else e.message) }
    }

    suspend fun testarGemini(chave: String): Result<Unit> = withContext(Dispatchers.IO) {
        val k = chave.trim()
        if (k.length < 30) return@withContext Result.failure(IllegalArgumentException("A chave parece incompleta. Ela começa com AIza."))
        runCatching {
            val url = "$geminiBase/v1beta/models".toHttpUrl().newBuilder().addQueryParameter("pageSize", "1").build()
            http.newCall(Request.Builder().url(url).header("x-goog-api-key", k).build()).execute().use { r ->
                if (!r.isSuccessful) error("O Google recusou essa chave. Copie de novo em Get API key.")
            }
        }.recoverCatching { e -> throw IllegalArgumentException(if (e is java.io.IOException) "Sem internet para testar. Tente de novo." else e.message) }
    }

    /** Bloco para o "Add from .env" do Render. */
    fun blocoEnv(): String = buildString {
        appendLine("APP_TOKEN=$appToken")
        telegram?.let { appendLine("TELEGRAM_BOT_TOKEN=$it") }
        gemini?.let { appendLine("GEMINI_API_KEY=$it") }
        banco?.let { appendLine("DATABASE_URL=$it") }
    }.trim()
}
