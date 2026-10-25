package com.remotesend.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PkgFile(
    val id: String,
    val uri: String,
    val displayName: String,
    val safeName: String,
    val sizeBytes: Long = 0L,
    val title: String? = null,
    val titleId: String? = null,
    val contentId: String? = null,
    val appVersion: String? = null,
    val category: String? = null,
    val iconPath: String? = null,
    val metadataRead: Boolean = false,
    val addedAt: Long = 0L,
) {
    val effectiveTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: displayName

    val hasMetadata: Boolean
        get() = titleId != null || contentId != null || title != null
}

data class ImportResult(
    val added: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val messages: List<String> = emptyList(),
)

data class PkgMetadata(
    val title: String? = null,
    val titleId: String? = null,
    val contentId: String? = null,
    val appVersion: String? = null,
    val category: String? = null,
    val icon: ByteArray? = null,
)
