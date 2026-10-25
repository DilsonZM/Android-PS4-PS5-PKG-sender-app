package com.remotesend.app.data.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import android.util.Log
import com.remotesend.app.RemoteSendApp
import com.remotesend.app.domain.model.InstallRuntime
import com.remotesend.app.domain.model.QueueStatus
import com.remotesend.app.domain.model.ServerRuntime
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class RemoteSendService : Service() {

    private val container by lazy { (application as RemoteSendApp).container }

    private val uncaughtHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("RemoteSend", "Excepción no capturada en el servicio", throwable)
    }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + uncaughtHandler,
    )
    private var observerJob: Job? = null
    private var foregroundStarted = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        container.notifications.createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVER
        if (!foregroundStarted) {
            startForegroundSafely()
        }

        when (action) {
            ACTION_START_SERVER -> startServer()
            ACTION_STOP_SERVER -> stopServer()
            ACTION_STOP_SERVICE -> stopEverything()
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        scope.launch {
            val state = container.serverManager.start()
            when (state) {
                is ServerRuntime.Running -> {
                    acquireWakeLock()
                    observeState()
                    updateForeground()
                    container.logger.success("Servicio", "Servicio en primer plano activo")
                }

                is ServerRuntime.Error -> {
                    updateForeground()
                    container.logger.error("Servicio", state.message)
                    delay(3_000L)
                    stopForegroundSafely()
                    stopSelf()
                }

                else -> updateForeground()
            }
        }
    }

    private fun stopServer() {
        scope.launch {
            container.serverManager.stop()
            releaseWakeLock()
            updateForeground()
            val hasActiveInstall = container.stateStore.snapshot().queue.any {
                it.status == QueueStatus.INSTALLING || it.status == QueueStatus.PAUSED
            }
            if (!hasActiveInstall) {
                delay(600L)
                stopForegroundSafely()
                stopSelf()
            }
        }
    }

    private fun stopEverything() {
        scope.launch {
            container.serverManager.stop()
            releaseWakeLock()
            stopForegroundSafely()
            stopSelf()
        }
    }

    private fun observeState() {
        if (observerJob != null) return
        observerJob = scope.launch {
            combine(
                container.serverManager.state,
                container.stateStore.state.map { state ->
                    state.queue.firstOrNull {
                        it.status == QueueStatus.INSTALLING || it.status == QueueStatus.PAUSED
                    }
                }.distinctUntilChanged(),
                container.installationManager.runtime,
            ) { server, activeItem, runtime ->
                Triple(server, activeItem, runtime)
            }.collect { (server, activeItem, runtime) ->
                if (server is ServerRuntime.Running) acquireWakeLock() else releaseWakeLock()
                if (foregroundStarted) {
                    val notification = container.notifications.buildServiceNotification(
                        server,
                        activeItem,
                        runtime,
                    )
                    runCatching {
                        getSystemService(NotificationManager::class.java)
                            ?.notify(NotificationHelper.SERVICE_ID, notification)
                    }
                }
            }
        }
    }

    private fun startForegroundSafely() {
        val state = container.serverManager.state.value
        val notification = container.notifications.buildServiceNotification(
            state,
            null,
            InstallRuntime.Idle,
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NotificationHelper.SERVICE_ID, notification, type)
            foregroundStarted = true
        }.onFailure { exception ->
            container.logger.error("Servicio", "No se pudo iniciar el servicio", exception.message)
        }
    }

    private fun updateForeground() {
        if (!foregroundStarted) return
        val notification = container.notifications.buildServiceNotification(
            container.serverManager.state.value,
            null,
            container.installationManager.runtime.value,
        )
        runCatching {
            getSystemService(NotificationManager::class.java)
                ?.notify(NotificationHelper.SERVICE_ID, notification)
        }
    }

    private fun stopForegroundSafely() {
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        foregroundStarted = false
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val lock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RemoteSend::Server",
        )
        lock.setReferenceCounted(false)
        runCatching { lock.acquire(WAKE_LOCK_TIMEOUT_MS) }
        wakeLock = lock
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        observerJob?.cancel()
        container.serverManager.stop()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_SERVER = "com.remotesend.app.START_SERVER"
        const val ACTION_STOP_SERVER = "com.remotesend.app.STOP_SERVER"
        const val ACTION_STOP_SERVICE = "com.remotesend.app.STOP_SERVICE"

        fun startServer(context: Context) {
            val intent = Intent(context, RemoteSendService::class.java)
                .setAction(ACTION_START_SERVER)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopServer(context: Context) {
            val intent = Intent(context, RemoteSendService::class.java)
                .setAction(ACTION_STOP_SERVER)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, RemoteSendService::class.java)
                .setAction(ACTION_STOP_SERVICE)
            ContextCompat.startForegroundService(context, intent)
        }

        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 60 * 1000L
    }
}
