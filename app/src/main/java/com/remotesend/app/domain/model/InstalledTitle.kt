package com.remotesend.app.domain.model

import com.remotesend.app.domain.util.PkgNaming

data class InstalledTitleStatus(
    val exists: Boolean,
    val sizeBytes: Long? = null,
)

data class InstalledTitle(
    val titleId: String,
    val title: String,
    val iconPath: String?,
    val installedSizeBytes: Long,
    val packages: List<PkgFile> = emptyList(),
) {
    val basePackage: PkgFile?
        get() = packages.firstOrNull {
            PkgNaming.isBaseCategory(
                PkgNaming.effectiveCategory(it.category, it.displayName),
            )
        } ?: packages.firstOrNull()

    val updates: List<PkgFile>
        get() = packages.filter {
            PkgNaming.isUpdateCategory(
                PkgNaming.effectiveCategory(it.category, it.displayName),
            )
        }

    val dlcs: List<PkgFile>
        get() = packages.filter {
            PkgNaming.isDlcCategory(
                PkgNaming.effectiveCategory(it.category, it.displayName),
            )
        }
}

data class InstalledScanResult(
    val titles: List<InstalledTitle> = emptyList(),
    val warning: String? = null,
    val unsupportedReason: String? = null,
)
