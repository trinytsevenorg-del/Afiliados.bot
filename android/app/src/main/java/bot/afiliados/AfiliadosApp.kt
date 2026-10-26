package bot.afiliados

import android.app.Application
import androidx.work.ExistingWorkPolicy
import bot.afiliados.data.BancoAfiliados
import bot.afiliados.data.Backend
import bot.afiliados.data.Backup
import bot.afiliados.data.Cofre
import bot.afiliados.data.Preferencias
import bot.afiliados.data.Repositorio
import bot.afiliados.domain.EstadoFila
import bot.afiliados.domain.TipoCanal
import bot.afiliados.work.Notificacoes
import bot.afiliados.work.PublicarWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Contêiner simples de dependências (sem Hilt: o app é pequeno). */
class AfiliadosApp : Application() {
    val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val banco by lazy { BancoAfiliados.criar(this) }
    val cofre by lazy { Cofre(this) }
    val prefs by lazy { Preferencias(this) }
    val backend by lazy { Backend({ prefs.backendUrl }, { cofre.ler(TOKEN) }) }
    val repo by lazy { Repositorio(banco.dao(), backend, prefs) }
    val backup by lazy { Backup(banco.dao()) }
    /** Tema atual, observado pela interface. */
    val tema by lazy { androidx.compose.runtime.mutableStateOf(prefs.tema) }
    /** Prints recebidos pelo "Compartilhar", esperando o formulário abrir. */
    val printPendente = androidx.compose.runtime.mutableStateOf<List<android.net.Uri>>(emptyList())

    override fun onCreate() {
        super.onCreate()
        Notificacoes.criarCanal(this)
        escopo.launch { recuperar() }
    }

    /** Ao abrir: trata itens interrompidos e garante que cada agendado tem sua tarefa. */
    private suspend fun recuperar() {
        repo.recuperarInterrompidos()
        val dao = repo.dao
        for (item in dao.itensNoEstado(EstadoFila.AGENDADO)) {
            val auto = item.canalId?.let { dao.canal(it) }?.tipo?.automatico == true
            PublicarWorker.agendar(this, item, auto, ExistingWorkPolicy.KEEP)
        }
        dao.limparAtividades(System.currentTimeMillis() - 90L * 24 * 3_600_000)
    }

    companion object {
        const val TOKEN = "backend_token"
    }
}
