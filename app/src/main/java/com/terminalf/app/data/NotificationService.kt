package com.terminalf.app.data

import com.terminalf.app.core.tr
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.terminalf.app.MainActivity
import com.terminalf.app.R
import com.terminalf.app.core.AlertEvent
import com.terminalf.app.state.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TASK_CHANNEL_ID = "tasks"
private const val ALERT_CHANNEL_ID = "alerts"

/**
 * `NotificationCompat` tabanlı gerçek uygulama. Her çağrı hataya karşı korunur: bildirim
 * gönderilemezse uygulama etkilenmez.
 */
class NotificationService(
    context: Context,
    private val bridge: ActivityBridge,
) : Notifier {

    private val appContext = context.applicationContext
    private var ready = false

    override suspend fun init() {
        if (ready) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = appContext.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(
                    NotificationChannel(TASK_CHANNEL_ID, tr("Task results", "İşlem sonuçları"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = tr("Notifies you when optimization and cleanup tasks finish.", "Optimizasyon ve temizlik işlemleri bittiğinde bildirir.")
                    },
                )
                nm.createNotificationChannel(
                    NotificationChannel(ALERT_CHANNEL_ID, tr("Health alerts", "Sağlık uyarıları"), NotificationManager.IMPORTANCE_HIGH).apply {
                        description = tr("Overheating, low battery, full storage and memory alerts.", "Isınma, düşük pil, dolu depolama ve bellek uyarıları.")
                    },
                )
            }
            ready = true
        } catch (_: Exception) {
            // Platform desteklemiyorsa sessizce yok say.
        }
    }

    override suspend fun hasPermission(): Boolean = try {
        val allowed = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT >= 33) {
            allowed && ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            allowed
        }
    } catch (_: Exception) {
        false
    }

    override suspend fun requestPermission(): Boolean {
        return try {
            if (hasPermission()) return true
            if (Build.VERSION.SDK_INT < 33) return false
            val requester = bridge.notificationPermissionRequester ?: return false
            requester()
            hasPermission()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun inForeground(): Boolean = withContext(Dispatchers.Main) {
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    override suspend fun showTask(title: String, body: String) {
        if (inForeground()) return
        show(1, title, body, TASK_CHANNEL_ID, important = false)
    }

    override suspend fun showAlert(event: AlertEvent) {
        show(100 + event.kind.ordinal, event.title, event.body, ALERT_CHANNEL_ID, important = true)
    }

    override suspend fun showTest() {
        show(2, tr("Notifications are working", "Bildirimler çalışıyor"), tr("TerminalF can send notifications on this device.", "TerminalF bu cihazda bildirim gönderebiliyor."), TASK_CHANNEL_ID, important = false)
    }

    private suspend fun show(id: Int, title: String, body: String, channelId: String, important: Boolean) {
        try {
            if (!ready) init()
            if (!hasPermission()) return
            val open = PendingIntent.getActivity(
                appContext,
                0,
                Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(if (important) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(appContext).notify(id, notification)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Bildirim gönderilemezse uygulama akışı bozulmaz.
        }
    }
}
