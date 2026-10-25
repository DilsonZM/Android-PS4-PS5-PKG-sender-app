package com.remotesend.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Serializable
enum class FrameRateMode {
    AUTO,
    FPS_60,
    FPS_120,
}

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val developerMode: Boolean = false,
    val frameRateMode: FrameRateMode = FrameRateMode.AUTO,
    val groupPackagesByTitleId: Boolean = true,
    val collapsedCusaGroups: Set<String> = emptySet(),
)

@Serializable
data class ServerConfig(
    val bindIp: String = "",
    val port: Int = 8337,
    val autoStart: Boolean = true,
    val autoAdvance: Boolean = true,
    val scanSubdirectories: Boolean = false,
    val readMetadata: Boolean = true,
    val autoScanOnStartup: Boolean = true,
    val scanPath: String = "/storage/emulated/0/Download",
    val lastScanTreeUri: String? = null,
    val lastScanFolderName: String? = null,
)
