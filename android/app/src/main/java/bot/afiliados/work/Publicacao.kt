package bot.afiliados.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import bot.afiliados.AfiliadosApp
import bot.afiliados.MainActivity
import bot.afiliados.data.ItemFila
import bot.afiliados.domain.EstadoFila
import bot.afiliados.domain.TipoCanal
import java.util.concurrent.TimeUnit

/**
 * Roda no horário agendado.
 * - Telegram: publica de verdade e só marca "publicado" com a confirmação.
 * - Compartilhar (WhatsApp etc.): só avisa com uma notificação; quem envia é você.
 */
class PublicarWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AfiliadosApp
        val repo = app.repo
        val id = inputData.getLong(ITEM, -1)
        val item = repo.dao.itemAgora(id) ?: return Result.success()
        if (item.estado != EstadoFila.AGENDADO) return Result.success()
        val canal = item.canalId?.let { repo.dao.canal(it) }
        if (canal?.tipo == TipoCanal.TELEGRAM) {
            val novo = repo.publicarAutomatico(id)
            val produto = repo.dao.produtoAgora(item.produtoId)?.nome ?: "Oferta"
            if (novo != EstadoFila.PUBLICADO_CONFIRMADO) {
                Notificacoes.mostrar(applicationContext, id, "Publicação não concluída", "$produto: ${novo.rotulo}")
            }
        } else {
            val produto = repo.dao.produtoAgora(item.produtoId)?.nome ?: "Oferta"
            Notificacoes.mostrar(applicationContext, id, "Hora de publicar", "$produto → ${canal?.nome ?: "compartilhar"}. Toque para revisar e enviar.")
            repo.registrar("fila", "Lembrete de publicação: $produto")
        }
        return Result.success()
    }

    companion object {
        const val ITEM = "item"
        fun nome(id: Long) = "publicar-$id"

        /** Agenda (ou reagenda) um item. KEEP evita duas tarefas para o mesmo item. */
        fun agendar(ctx: Context, item: ItemFila, automatico: Boolean, politica: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
            val atraso = ((item.planejadoPara ?: System.currentTimeMillis()) - System.currentTimeMillis()).coerceAtLeast(0)
            val req = OneTimeWorkRequestBuilder<PublicarWorker>()
                .setInitialDelay(atraso, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(ITEM to item.id))
                .apply { if (automatico) setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()) }
                .addTag("publicacao")
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(nome(item.id), politica, req)
        }

        fun cancelar(ctx: Context, id: Long) {
            WorkManager.getInstance(ctx).cancelUniqueWork(nome(id))
        }
    }
}

object Notificacoes {
    private const val CANAL = "publicacoes"

    fun criarCanal(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Publicações", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Lembretes e resultados das ofertas agendadas"
        })
    }

    fun mostrar(ctx: Context, itemId: Long, titulo: String, texto: String) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val abrir = PendingIntent.getActivity(
            ctx, itemId.toInt(),
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ITEM, itemId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(itemId.toInt(), n)
    }
}
