package com.remotesend.app.data.network

import com.remotesend.app.core.AppLogger
import com.remotesend.app.domain.model.ConsoleConfig
import com.remotesend.app.domain.model.ConsoleHealth
import com.remotesend.app.domain.model.ConsoleInstallResult
import com.remotesend.app.domain.model.ConsoleType
import com.remotesend.app.domain.model.PkgFile
import com.remotesend.app.domain.model.TaskProgress
import com.remotesend.app.domain.util.PkgNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * TCP client used by PS4 GoldHEN and PS5 etaHEN. Both expose a raw socket
 * RPI endpoint that receives a JSON object and returns a JSON response.
 */
class TcpConsoleClient(
    private val config: ConsoleConfig,
    override val type: ConsoleType,
    private val logger: AppLogger,
) : ConsoleClient {

    override suspend fun checkConnection(): ConsoleHealth = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        return@withContext try {
            connectSocket().use {
                val elapsed = System.currentTimeMillis() - started
                ConsoleHealth(
                    reachable = true,
                    message = "Socket RPI accesible en ${config.host}:${config.tcpPort}",
                    latencyMs = elapsed,
                )
            }
        } catch (exception: Exception) {
            ConsoleHealth(
                reachable = false,
                message = connectError(exception),
            )
        }
    }

    override suspend fun install(
        pkg: PkgFile,
        pkgUrl: String,
        iconUrl: String?,
    ): ConsoleInstallResult = withContext(Dispatchers.IO) {
        val payload = if (type == ConsoleType.PS5_ETAHEN) {
            buildJsonObject { put("url", pkgUrl) }
        } else {
            val contentId = pkg.contentId
                ?: pkg.titleId
                ?: PkgNaming.contentIdFromName(pkg.displayName)
            if (contentId.isNullOrBlank()) {
                return@withContext ConsoleInstallResult.Failure(
                    "GoldHEN necesita el CONTENT_ID del PKG. Activa la lectura de metadatos " +
                        "o usa un PKG con el Content ID en el nombre.",
                )
            }
            buildJsonObject {
                put("id", contentId)
                put("contentUrl", pkgUrl)
                put("iconPath", iconUrl ?: "")
                put("contentName", pkg.effectiveTitle)
            }
        }

        val response = try {
            sendAndReceive(payload)
        } catch (exception: Exception) {
            return@withContext ConsoleInstallResult.Failure(connectError(exception))
        }

        val json = response.json
        if (json == null) {
            logger.warn("TCP", "Respuesta no JSON de la consola", response.raw.take(300))
            return@withContext ConsoleInstallResult.Failure(
                "Respuesta inválida de la consola: ${response.raw.take(120)}",
            )
        }

        logger.debug("TCP", "Respuesta de instalación", json.toString())

        val result = json.longValue("res") ?: json.longValue("result")
        if (result == null) {
            val success = json.booleanValue("success")
            if (success == true) {
                return@withContext ConsoleInstallResult.Accepted()
            }
            return@withContext ConsoleInstallResult.Failure(
                json.stringValue("error") ?: json.stringValue("message")
                ?: "La consola no devolvió un código de resultado",
            )
        }

        if (result == 0L) {
            return@withContext ConsoleInstallResult.Accepted()
        }

        if (result == -2135809020L) {
            return@withContext ConsoleInstallResult.Failure(consoleErrorMessage(result), result)
        }

        ConsoleInstallResult.Failure(consoleErrorMessage(result), result)
    }

    override suspend fun taskProgress(taskId: Long): TaskProgress = TaskProgress.Unsupported

    override suspend fun pause(taskId: Long): Boolean = false

    override suspend fun stop(taskId: Long): Boolean = false

    override suspend fun resume(taskId: Long): Boolean = false

    override suspend fun unregister(taskId: Long): Boolean = false

    private fun connectSocket(): Socket {
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.connect(
            InetSocketAddress(config.host, config.tcpPort),
            config.timeoutMs.toInt().coerceAtLeast(1),
        )
        socket.soTimeout = config.timeoutMs.toInt().coerceAtLeast(1)
        return socket
    }

    private fun sendAndReceive(payload: JsonObject): TcpResponse {
        connectSocket().use { socket ->
            val output = socket.getOutputStream()
            output.write(payload.toString().toByteArray(Charsets.UTF_8))
            output.flush()

            val input = socket.getInputStream()
            val buffer = ByteArray(4 * 1024)
            val received = ByteArrayOutputStream()
            while (received.size() < MAX_RESPONSE_BYTES) {
                val read = try {
                    input.read(buffer)
                } catch (_: SocketTimeoutException) {
                    break
                }
                if (read < 0) break
                received.write(buffer, 0, read)
                val text = received.toString(Charsets.UTF_8.name()).trim()
                if (text.endsWith("}") && parseJsonObject(text) != null) {
                    break
                }
            }

            val raw = received.toString(Charsets.UTF_8.name()).trim()
            return TcpResponse(parseJsonObject(raw), raw)
        }
    }

    private fun connectError(exception: Exception): String {
        return when {
            exception is SocketTimeoutException ||
                exception.message?.contains("timed out", ignoreCase = true) == true ->
                "Tiempo de espera agotado con ${config.host}:${config.tcpPort}"

            exception.message?.contains("refused", ignoreCase = true) == true ->
                "Conexión rechazada en ${config.host}:${config.tcpPort}. " +
                    "¿Está GoldHEN/etaHEN con RPI activo?"

            else -> "No se pudo conectar con ${config.host}:${config.tcpPort}: ${exception.message}"
        }
    }

    private data class TcpResponse(
        val json: JsonObject?,
        val raw: String,
    )

    companion object {
        private const val MAX_RESPONSE_BYTES = 128 * 1024
    }
}
