package com.remotesend.app.data.network

import com.remotesend.app.domain.model.ConsoleHealth
import com.remotesend.app.domain.model.ConsoleInstallResult
import com.remotesend.app.domain.model.ConsoleType
import com.remotesend.app.domain.model.DiskSpace
import com.remotesend.app.domain.model.InstalledTitleStatus
import com.remotesend.app.domain.model.PkgFile
import com.remotesend.app.domain.model.TaskProgress

interface ConsoleClient {
    val type: ConsoleType

    suspend fun checkConnection(): ConsoleHealth

    suspend fun install(
        pkg: PkgFile,
        pkgUrl: String,
        iconUrl: String?,
    ): ConsoleInstallResult

    suspend fun taskProgress(taskId: Long): TaskProgress

    suspend fun pause(taskId: Long): Boolean

    suspend fun stop(taskId: Long): Boolean

    suspend fun resume(taskId: Long): Boolean

    suspend fun unregister(taskId: Long): Boolean

    suspend fun isInstalled(titleId: String): InstalledTitleStatus? = null

    suspend fun diskSpace(): DiskSpace? = null

    suspend fun uninstallGame(titleId: String): Boolean = false

    suspend fun uninstallPatch(titleId: String): Boolean = false

    suspend fun uninstallAc(contentId: String): Boolean = false

    suspend fun uninstallTheme(contentId: String): Boolean = false
}
