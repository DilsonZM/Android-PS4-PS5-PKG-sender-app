package com.remotesend.app.data.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
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
import com.remotesend.app.MainActivity
import com.remotesend.app.R
import com.remotesend.app.domain.model.InstallRuntime
import com.remotesend.app.domain.model.QueueItem
import com.remotesend.app.domain.model.ServerRuntime
import com.remotesend.app.domain.util.PkgNaming

class NotificationHelper(
    private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        createChannels()
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val systemManager = context.getSystemService(NotificationManager::class.java) ?: return

        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL,
            context.getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.service_channel_description)
            setShowBadge(false)
        }

        val installChannel = NotificationChannel(
            INSTALL_CHANNEL,
            context.getString(R.string.install_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.install_channel_description)
        }

        systemManager.createNotificationChannel(serviceChannel)
        systemManager.createNotificationChannel(installChannel)
    }

    fun buildServiceNotification(
        server: ServerRuntime,
        latestItem: QueueItem?,
        runtime: InstallRuntime,
    ): Notification {
        val title = when (server) {
            is ServerRuntime.Running -> "Servidor activo"
            ServerRuntime.Starting -> "Iniciando servidor"
            is ServerRuntime.Error -> "Error del servidor"
            ServerRuntime.Stopped -> "RemoteSend"
        }

        val text = buildString {
            when (server) {
                is ServerRuntime.Running -> append("${server.baseUrl} · sirviendo PKG")
                is ServerRuntime.Error -> append(server.message)
                ServerRuntime.Starting -> append("Preparando el servidor HTTP…")
                ServerRuntime.Stopped -> append("Servidor detenido")
            }
            val activeTitle = when (runtime) {
                is InstallRuntime.Working -> runtime.title
                else -> latestItem?.takeIf {
                    it.status == com.remotesend.app.domain.model.QueueStatus.INSTALLING
                }?.effectiveTitle
            }
            if (activeTitle != null) {
                append(" · Instalando: ").append(activeTitle)
            }
        }

        val builder = NotificationCompat.Builder(context, SERVICE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remotesend)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent())

        if (server is ServerRuntime.Running && runtime !is InstallRuntime.Working) {
            builder.addAction(
                0,
                "Detener servidor",
                PendingIntent.getService(
                    context,
                    ACTION_STOP_REQUEST,
                    Intent(context, RemoteSendService::class.java)
                        .setAction(RemoteSendService.ACTION_STOP_SERVER),
                    pendingIntentFlags(),
                ),
            )
        }

        if (runtime is InstallRuntime.Working) {
            builder.setProgress(100, runtime.progress, false)
        }

        return builder.build()
    }

    fun showInstallProgress(item: QueueItem) {
        if (!notificationsEnabled()) return
        val text = buildString {
            append("${item.progress}%")
            if (item.totalBytes > 0) {
                append(" · ")
                append(PkgNaming.formatBytes(item.transferredBytes))
                append(" / ")
                append(PkgNaming.formatBytes(item.totalBytes))
            } else {
                append(" · ").append(PkgNaming.formatBytes(item.sizeBytes))
            }
            PkgNaming.formatSpeed(item.speedBytesPerSecond)?.let {
                append(" · ").append(it)
            }
            val eta = when {
                item.restSeconds > 0 -> item.restSeconds
                item.speedBytesPerSecond > 0 && item.totalBytes > item.transferredBytes ->
                    (item.totalBytes - item.transferredBytes) / item.speedBytesPerSecond
                else -> 0L
            }
            PkgNaming.formatDuration(eta)?.let { append(" · quedan ").append(it) }
            item.message?.let { append(" · ").append(it) }
        }
        val notification = NotificationCompat.Builder(context, INSTALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remotesend)
            .setContentTitle("Instalando ${item.effectiveTitle}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setProgress(100, item.progress.coerceIn(0, 100), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .build()
        safeNotify(INSTALL_PROGRESS_ID, notification)
    }

    fun notifyStarted(item: QueueItem) {
        if (!notificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, INSTALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remotesend)
            .setContentTitle("Solicitud enviada")
            .setContentText("${item.effectiveTitle} se está instalando en la consola")
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        safeNotify(INSTALL_PROGRESS_ID, notification)
    }

    fun notifyCompleted(item: QueueItem) {
        if (!notificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, INSTALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remotesend)
            .setContentTitle("Instalación completada")
            .setContentText(item.effectiveTitle)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        safeNotify(INSTALL_RESULT_ID, notification)
    }

    fun notifySent(item: QueueItem) {
        if (!notificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, INSTALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remotesend)
            .setContentTitle("Enviado a la consola")
            .setContentText("${item.effectiveTitle} fue enviado. Revisa la cola de descargas de la consola.")
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        safeNotify(INSTALL_RESULT_ID, notification)
    }

    fun notifyFailed(item: QueueItem, message: String) {
        if (!notificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, INSTALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remotesend)
            .setContentTitle("Error de instalación")
            .setContentText("${item.effectiveTitle}: $message")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${item.effectiveTitle}: $message"))
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        safeNotify(INSTALL_RESULT_ID, notification)
    }

    fun cancelInstallProgress() {
        runCatching { manager.cancel(INSTALL_PROGRESS_ID) }
    }

    fun notificationsEnabled(): Boolean {
        return runCatching { manager.areNotificationsEnabled() }.getOrDefault(true)
    }

    @SuppressLint("MissingPermission")
    private fun safeNotify(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { manager.notify(id, notification) }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, pendingIntentFlags())
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    companion object {
        const val SERVICE_ID = 1001
        const val INSTALL_PROGRESS_ID = 1002
        const val INSTALL_RESULT_ID = 1003
        const val SERVICE_CHANNEL = "remotesend_service"
        const val INSTALL_CHANNEL = "remotesend_installs"
        private const val ACTION_STOP_REQUEST = 11
    }
}
