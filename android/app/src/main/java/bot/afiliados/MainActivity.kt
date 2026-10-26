package bot.afiliados

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

class MainActivity : FragmentActivity() {
    /** Link recebido pelo "Compartilhar" de outro app, ou item vindo de notificação. */
    val linkRecebido = mutableStateOf<String?>(null)
    val itemAberto = mutableStateOf<Long?>(null)
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
            TemaAfiliados {
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
