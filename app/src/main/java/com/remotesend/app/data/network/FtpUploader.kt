package com.remotesend.app.data.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.remotesend.app.core.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File

data class FtpUpload(
    val fileName: String,
    val remotePath: String,
    val bytes: Long,
)

class FtpUploader(
    private val context: Context,
    private val logger: AppLogger,
) {
    suspend fun upload(
        host: String,
        port: Int,
        remoteDir: String,
        uri: Uri,
    ): Result<FtpUpload> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var displayName = "archivo_${System.currentTimeMillis()}.bin"
        var declaredSize = 0L

        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    declaredSize = cursor.getLong(sizeIndex)
                }
            }
        }

        val safeName = File(displayName.substringAfterLast('/').substringAfterLast('\\'))
            .name
            .ifBlank { "archivo_${System.currentTimeMillis()}.bin" }
        val targetDir = remoteDir.trimEnd('/').ifBlank { "/user/data/RemoteSend" }

        val ftp = FTPClient()
        ftp.connectTimeout = 8_000
        ftp.defaultTimeout = 15_000
        ftp.setDataTimeout(30_000)

        try {
            ftp.connect(host, port)
            check(ftp.login("anonymous", "")) {
                "GoldHEN FTP rechazó el acceso anónimo"
            }
            ftp.enterLocalPassiveMode()
            ftp.setFileType(FTP.BINARY_FILE_TYPE)

            if (!ftp.changeWorkingDirectory(targetDir)) {
                ftp.makeDirectory(targetDir)
                check(ftp.changeWorkingDirectory(targetDir)) {
                    "No se pudo crear la carpeta $targetDir en la PS4"
                }
            }

            val input = resolver.openInputStream(uri)
                ?: error("No se pudo abrir el archivo seleccionado")
            input.use {
                check(ftp.storeFile(safeName, it)) {
                    "GoldHEN FTP rechazó el archivo $safeName"
                }
            }

            val remoteSize = runCatching { ftp.size(safeName) }
                .getOrDefault(-1)
                .toLong()
                .takeIf { it > 0L }
                ?: declaredSize
            val remotePath = "$targetDir/$safeName"
            logger.info("FTP", "Archivo enviado a $remotePath ($remoteSize bytes)")
            Result.success(FtpUpload(safeName, remotePath, remoteSize))
        } catch (exception: Exception) {
            logger.warn("FTP", "Error subiendo archivo", exception.message)
            Result.failure(exception)
        } finally {
            runCatching {
                if (ftp.isConnected) {
                    ftp.logout()
                    ftp.disconnect()
                }
            }
        }
    }
}
