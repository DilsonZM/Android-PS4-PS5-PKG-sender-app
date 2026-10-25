package com.remotesend.app.di

import android.app.Application
import android.net.Uri
import android.content.Context
import android.util.Log
import com.remotesend.app.core.AppLogger
import com.remotesend.app.core.StorageAccess
import com.remotesend.app.data.backup.BackupManager
import com.remotesend.app.data.install.InstallationManager
import com.remotesend.app.data.log.DataStoreLogger
import com.remotesend.app.data.network.ConsoleClientFactory
import com.remotesend.app.data.network.ConsoleDetector
import com.remotesend.app.data.network.LanScanner
import com.remotesend.app.data.persist.StateStore
import com.remotesend.app.data.pkg.PkgMetadataReader
import com.remotesend.app.data.repository.PackageImporter
import com.remotesend.app.data.repository.PackageRepository
import com.remotesend.app.data.repository.QueueRepository
import com.remotesend.app.data.repository.SettingsRepository
import com.remotesend.app.data.server.ServerManager
import com.remotesend.app.data.service.NotificationHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(
    private val application: Application,
) {
    val appContext: Context = application.applicationContext

    private val uncaughtCoroutineHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("RemoteSend", "Excepción no capturada en una corrutina", throwable)
    }

    val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + uncaughtCoroutineHandler,
    )

    val stateStore = StateStore(application)
    val logger: AppLogger = DataStoreLogger(stateStore, applicationScope)

    val packageRepository = PackageRepository(application, stateStore, logger)
    val queueRepository = QueueRepository(stateStore)
    val settingsRepository = SettingsRepository(stateStore)

    val packageImporter = PackageImporter(
        context = application,
        stateStore = stateStore,
        metadataReader = PkgMetadataReader(logger),
        logger = logger,
    )

    val serverManager = ServerManager(
        context = application,
        stateStore = stateStore,
        packageRepository = packageRepository,
        logger = logger,
        scope = applicationScope,
    )

    val notifications = NotificationHelper(application)

    private val clientFactory = ConsoleClientFactory(logger)
    val consoleDetector = ConsoleDetector(clientFactory, logger)
    val lanScanner = LanScanner(logger)

    val installationManager = InstallationManager(
        stateStore = stateStore,
        packages = packageRepository,
        queue = queueRepository,
        serverManager = serverManager,
        detector = consoleDetector,
        clientFactory = clientFactory,
        notifier = notifications,
        logger = logger,
        scope = applicationScope,
    )

    val backupManager = BackupManager(application, stateStore, logger)

    init {
        applicationScope.launch {
            runCatching {
                val state = stateStore.snapshot()
                if (!state.server.autoScanOnStartup) return@runCatching

                if (StorageAccess.hasAllFilesAccess(application) &&
                    state.server.scanPath.isNotBlank()
                ) {
                    val result = packageImporter.importFromPath(
                        rootPath = state.server.scanPath,
                        scanSubdirectories = state.server.scanSubdirectories,
                        readMetadata = state.server.readMetadata,
                    )
                    logger.info(
                        "Escaneo",
                        "Auto-escaneo: ${result.added} nuevos, ${result.skipped} existentes",
                    )
                    return@runCatching
                }

                val tree = state.server.lastScanTreeUri ?: return@runCatching
                val result = packageImporter.importTree(
                    treeUri = Uri.parse(tree),
                    scanSubdirectories = state.server.scanSubdirectories,
                    readMetadata = state.server.readMetadata,
                )
                logger.info(
                    "Escaneo",
                    "Auto-escaneo: ${result.added} nuevos, ${result.skipped} existentes",
                )
            }.onFailure { exception ->
                logger.warn("Escaneo", "Auto-escaneo falló", exception.message)
            }
        }
    }
}
