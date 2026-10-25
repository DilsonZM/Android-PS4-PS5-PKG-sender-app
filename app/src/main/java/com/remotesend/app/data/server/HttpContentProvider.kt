package com.remotesend.app.data.server

import java.io.InputStream

data class ServerPackage(
    val id: String,
    val safeName: String,
    val displayName: String,
    val title: String?,
    val titleId: String?,
    val sizeBytes: Long,
    val hasIcon: Boolean,
)

interface HttpContentProvider {
    suspend fun packages(): List<ServerPackage>
    suspend fun openStream(packageId: String, offset: Long): InputStream?
    suspend fun icon(packageId: String): ByteArray?
}
