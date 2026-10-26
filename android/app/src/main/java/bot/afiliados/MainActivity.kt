package bot.afiliados

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import bot.afiliados.domain.Links
import bot.afiliados.ui.AppRaiz
import bot.afiliados.ui.TemaAfiliados
import bot.afiliados.ui.temaEscuro

class MainActivity : FragmentActivity() {
    /** Link recebido pelo "Compartilhar" de outro app, ou item vindo de notificação. */
    val linkRecebido = mutableStateOf<String?>(null)
    val itemAberto = mutableStateOf<Long?>(null)
    val printRecebido = mutableStateOf(false)
    private val desbloqueado = mutableStateOf(false)

    private val pedirNotificacao = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lerIntent(intent)
        val app = application as AfiliadosApp
        desbloqueado.value = !app.prefs.bloqueioLocal
        if (Build.VERSION.SDK_INT >= 33) pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val escuro = temaEscuro(app.tema.value)
            LaunchedEffect(escuro) {
                val barra = if (escuro) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(barra, barra)
            }
            TemaAfiliados(app.tema.value) {
                AppRaiz(app = app, atividade = this, desbloqueado = desbloqueado.value, aoDesbloquear = ::desbloquear)
            }
        }
        if (!desbloqueado.value) desbloquear()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lerIntent(intent)
    }

    private fun lerIntent(i: Intent?) {
        if (i == null) return
        val tipo = i.type.orEmpty()
        if ((i.action == Intent.ACTION_SEND || i.action == Intent.ACTION_SEND_MULTIPLE) && tipo.startsWith("image/")) {
            val uris = if (i.action == Intent.ACTION_SEND_MULTIPLE) i.extras?.let { androidx.core.content.IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, android.net.Uri::class.java) }.orEmpty()
            else listOfNotNull(androidx.core.content.IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, android.net.Uri::class.java))
            if (uris.isNotEmpty()) { (application as AfiliadosApp).printPendente.value = uris.take(3); printRecebido.value = true }
        }
        if (i.action == Intent.ACTION_SEND && i.type == "text/plain") {
            linkRecebido.value = i.getStringExtra(Intent.EXTRA_TEXT)?.let { Links.extrairDeTexto(it) }
        }
        i.getLongExtra(EXTRA_ITEM, -1).takeIf { it > 0 }?.let { itemAberto.value = it }
    }

    /** Proteção simples de acesso: digital/rosto ou o bloqueio de tela do aparelho. */
    fun desbloquear() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                desbloqueado.value = true
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Afiliados.bot")
                .setSubtitle("Desbloqueie para abrir")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build(),
        )
    }

    companion object {
        const val EXTRA_ITEM = "item"
    }
}
