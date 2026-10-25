package com.remotesend.app.domain.model

import com.remotesend.app.domain.util.PkgNaming

data class PackageGroup(
    val key: String,
    val title: String,
    val titleId: String?,
    val packages: List<PkgFile>,
) {
    val totalBytes: Long
        get() = packages.sumOf { it.sizeBytes }

    val cover: PkgFile
        get() = packages.firstOrNull { PkgNaming.isBaseCategory(it.category) } ?: packages.first()
}
