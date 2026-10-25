package com.remotesend.app.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.remotesend.app.core.AppLogger
import com.remotesend.app.data.network.ConsoleClientFactory
import com.remotesend.app.data.network.ConsoleDetector
import com.remotesend.app.data.network.Ps4RpiClient
import com.remotesend.app.data.persist.StateStore
import com.remotesend.app.domain.model.ConsoleType
import com.remotesend.app.domain.model.InstalledScanResult
import com.remotesend.app.domain.model.InstalledTitle
import com.remotesend.app.domain.model.PkgFile
import com.remotesend.app.domain.util.PkgNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File

class InstalledRepository(
    private val context: Context,
    private val stateStore: StateStore,
    private val detector: ConsoleDetector,
    private val clientFactory: ConsoleClientFactory,
    private val logger: AppLogger,
) {
    suspend fun scan(): InstalledScanResult = withContext(Dispatchers.IO) {
        val console = stateStore.snapshot().console
        val libraryByTitle = sortedLibraryByTitle()

        val ftpResult = runCatching {
            scanViaFtp(console.host, libraryByTitle)
        }
        if (ftpResult.isSuccess) {
            val titles = ftpResult.getOrThrow()
            logger.info(
                "Instalados",
                "app.db por FTP: ${titles.size} juego(s) instalado(s)",
            )
            return@withContext InstalledScanResult(titles = titles)
        }

        val ftpError = ftpResult.exceptionOrNull()
        logger.warn("Instalados", "No se pudo leer app.db por FTP", ftpError?.message)

        val clientResult = resolveClient()
        if (clientResult.isFailure) {
            val reason = buildString {
                append("No se pudo leer app.db por FTP (GoldHEN 2121) ni conectar con PS4RPI. ")
                append(ftpError?.message ?: clientResult.exceptionOrNull()?.message.orEmpty())
            }.trim()
            return@withContext InstalledScanResult(unsupportedReason = reason)
        }

        val client = clientResult.getOrThrow()
        val fallback = scanLibraryWithRpi(client, libraryByTitle)
        val warning = buildString {
            append("No se pudo leer app.db por FTP de GoldHEN (2121). ")
            append("Mostrando solo títulos de la biblioteca confirmados por PS4RPI.")
            fallback.warning?.let {
                append(' ')
                append(it)
            }
        }
        fallback.copy(warning = warning)
    }

    suspend fun uninstallGame(titleId: String): Result<Unit> =
        withClient { it.uninstallGame(titleId) }

    suspend fun uninstallUpdate(titleId: String): Result<Unit> =
        withClient { it.uninstallPatch(titleId) }

    suspend fun uninstallDlc(contentId: String): Result<Unit> =
        withClient { it.uninstallAc(contentId) }

    private suspend fun sortedLibraryByTitle(): Map<String, List<PkgFile>> {
        return stateStore.snapshot().packages
            .filter { !it.titleId.isNullOrBlank() }
            .groupBy { it.titleId!! }
            .mapValues { (_, group) -> sortPackages(group) }
    }

    private fun sortPackages(group: List<PkgFile>): List<PkgFile> {
        return group.sortedWith(
            compareBy<PkgFile>(
                {
                    PkgNaming.packageSortRank(
                        PkgNaming.effectiveCategory(it.category, it.displayName),
                    )
                },
                { it.appVersion ?: "" },
                { it.displayName.lowercase() },
            ),
        )
    }

    private fun scanViaFtp(
        host: String,
        libraryByTitle: Map<String, List<PkgFile>>,
    ): List<InstalledTitle> {
        require(host.isNotBlank()) { "Configura la IP de la consola primero" }

        val dbFile = File(context.cacheDir, "ps4-app.db")
        val tempDb = File(context.cacheDir, "ps4-app.db.tmp")
        val iconDir = File(context.cacheDir, "ps4-installed-icons").apply { mkdirs() }
        val ftp = FTPClient()
        ftp.connectTimeout = 8_000
        ftp.defaultTimeout = 12_000
        ftp.setDataTimeout(20_000)

        try {
            ftp.connect(host, GOLDHEN_FTP_PORT)
            check(ftp.login("anonymous", "")) {
                "GoldHEN FTP rechazó el acceso anónimo"
            }
            ftp.enterLocalPassiveMode()
            ftp.setFileType(FTP.BINARY_FILE_TYPE)

            tempDb.delete()
            tempDb.outputStream().use { output ->
                check(ftp.retrieveFile(APP_DB_PATH, output)) {
                    "GoldHEN FTP no pudo descargar app.db"
                }
            }
            check(tempDb.length() > 0L) { "app.db llegó vacío" }
            if (dbFile.exists()) dbFile.delete()
            if (!tempDb.renameTo(dbFile)) {
                tempDb.copyTo(dbFile, overwrite = true)
                tempDb.delete()
            }

            val apps = parseAppDb(dbFile)
            return apps.map { app ->
                val sorted = libraryByTitle[app.titleId]
                val base = sorted?.firstOrNull {
                    PkgNaming.isBaseCategory(
                        PkgNaming.effectiveCategory(it.category, it.displayName),
                    )
                }
                val icon = base?.iconPath ?: ensureIcon(ftp, app, iconDir)
                InstalledTitle(
                    titleId = app.titleId,
                    title = app.title,
                    iconPath = icon,
                    installedSizeBytes = app.sizeBytes.takeIf { it > 0L }
                        ?: base?.sizeBytes
                        ?: 0L,
                    packages = sorted.orEmpty(),
                    isSystem = app.isSystem,
                )
            }.sortedBy { it.title.lowercase() }
        } finally {
            runCatching {
                if (ftp.isConnected) {
                    ftp.logout()
                    ftp.disconnect()
                }
            }
        }
    }

    private fun parseAppDb(file: File): List<InstalledApp> {
        val result = LinkedHashMap<String, InstalledApp>()
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { db ->
            val tables = mutableListOf<String>()
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' " +
                    "AND name LIKE 'tbl_appbrowse_%'",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    tables += cursor.getString(0)
                }
            }

            for (table in tables) {
                db.rawQuery(
                    "SELECT titleId, titleName, contentId, contentSize, metaDataPath, " +
                        "category FROM `$table` WHERE category IN ('gd','gdi','gda','gde') " +
                        "AND visible=1 AND contentStatus=0",
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val titleId = cursor.getString(0)?.trim().orEmpty()
                        val category = cursor.getString(5)?.trim().orEmpty()
                        val isSystem = category != "gd"
                        if (!isSystem && !TITLE_ID_PATTERN.matches(titleId)) continue
                        val name = cursor.getString(1)?.trim().orEmpty()
                        if (name.isEmpty()) continue
                        val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                        val app = InstalledApp(
                            titleId = titleId,
                            title = name,
                            sizeBytes = size,
                            metaDataPath = cursor.getString(4),
                            contentId = cursor.getString(2),
                            isSystem = isSystem,
                        )
                        val existing = result[titleId]
                        if (existing == null ||
                            (existing.isSystem && !app.isSystem) ||
                            (existing.isSystem == app.isSystem &&
                                existing.sizeBytes == 0L && app.sizeBytes > 0L)
                        ) {
                            result[titleId] = app
                        }
                    }
                }
            }
        }
        return result.values.toList()
    }

    private fun ensureIcon(
        ftp: FTPClient,
        app: InstalledApp,
        iconDir: File,
    ): String? {
        val cached = File(iconDir, "${app.titleId}.png")
        if (cached.length() > 0L) return cached.absolutePath

        val remoteDir = (app.metaDataPath ?: "/user/appmeta/${app.titleId}")
            .trimEnd('/')
        val temp = File(iconDir, "${app.titleId}.tmp")
        return runCatching {
            temp.delete()
            val copied = temp.outputStream().use { output ->
                ftp.retrieveFile("$remoteDir/icon0.png", output)
            }
            if (!copied || temp.length() <= 0L) {
                temp.delete()
                return null
            }
            cached.delete()
            if (!temp.renameTo(cached)) {
                temp.copyTo(cached, overwrite = true)
                temp.delete()
            }
            cached.absolutePath
        }.getOrNull()
    }

    private suspend fun scanLibraryWithRpi(
        client: Ps4RpiClient,
        libraryByTitle: Map<String, List<PkgFile>>,
    ): InstalledScanResult {
        var failures = 0
        val titles = mutableListOf<InstalledTitle>()

        for ((titleId, sorted) in libraryByTitle) {
            val status = client.isInstalled(titleId)
            if (status == null) {
                failures++
                continue
            }
            if (!status.exists) {
                delay(75)
                continue
            }

            val base = sorted.firstOrNull {
                PkgNaming.isBaseCategory(
                    PkgNaming.effectiveCategory(it.category, it.displayName),
                )
            }
            val display = base ?: sorted.first()
            titles += InstalledTitle(
                titleId = titleId,
                title = display.effectiveTitle,
                iconPath = base?.iconPath
                    ?: sorted.firstNotNullOfOrNull { it.iconPath },
                installedSizeBytes = status.sizeBytes
                    ?: base?.sizeBytes
                    ?: display.sizeBytes,
                packages = sorted,
            )
            delay(75)
        }

        return InstalledScanResult(
            titles = titles.sortedBy { it.title.lowercase() },
            warning = if (failures > 0) {
                "No se pudo consultar $failures título(s) en la consola"
            } else {
                null
            },
        )
    }

    private suspend fun withClient(
        action: suspend (Ps4RpiClient) -> Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val client = resolveClient().getOrElse {
            return@withContext Result.failure(it)
        }
        try {
            if (action(client)) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException("La consola rechazó la desinstalación"),
                )
            }
        } catch (exception: Exception) {
            logger.warn("Instalados", "Error desinstalando", exception.message)
            Result.failure(exception)
        }
    }

    private suspend fun resolveClient(): Result<Ps4RpiClient> {
        val config = stateStore.snapshot().console
        if (config.host.isBlank()) {
            return Result.failure(
                IllegalStateException("Configura la IP de la consola primero"),
            )
        }

        val detection = detector.detect(config)
        if (!detection.reachable) {
            return Result.failure(IllegalStateException(detection.message))
        }

        val resolvedType = if (config.type == ConsoleType.AUTO) {
            detection.type
        } else {
            config.type
        }
        if (resolvedType != ConsoleType.PS4_RPI) {
            return Result.failure(
                IllegalStateException(
                    "La desinstalación necesita PS4 con PS4RPI/flatZ RPI. " +
                        "La lista se lee por FTP de GoldHEN; RPI no expone la lista.",
                ),
            )
        }

        val rpiConfig = config.copy(
            type = ConsoleType.PS4_RPI,
            httpPort = detection.httpPort ?: config.httpPort,
        )
        return Result.success(clientFactory.createRpi(rpiConfig))
    }

    private data class InstalledApp(
        val titleId: String,
        val title: String,
        val sizeBytes: Long,
        val metaDataPath: String?,
        val contentId: String?,
        val isSystem: Boolean,
    )

    private companion object {
        const val GOLDHEN_FTP_PORT = 2121
        const val APP_DB_PATH = "/system_data/priv/mms/app.db"
        val TITLE_ID_PATTERN = Regex(
            "^(CUSA|SLUS|SLES|BLUS|BLES|NPXX|PCSE|PCSB|PCSG|PCSH|PCSC|" +
                "ULUS|ULJM|ULES|ULKS|NPJH|NPEB|NPEA|NPHB|NPHA|NPJA|NPJB)\\d{5}$",
        )
    }
}
