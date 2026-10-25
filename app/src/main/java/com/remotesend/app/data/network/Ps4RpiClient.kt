package com.remotesend.app.data.network

import com.remotesend.app.core.AppLogger
import com.remotesend.app.domain.model.ConsoleConfig
import com.remotesend.app.domain.model.ConsoleHealth
import com.remotesend.app.domain.model.ConsoleInstallResult
import com.remotesend.app.domain.model.ConsoleType
import com.remotesend.app.domain.model.PkgFile
import com.remotesend.app.domain.model.TaskProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class Ps4RpiClient(
    private val config: ConsoleConfig,
    private val logger: AppLogger,
) : ConsoleClient {

    override val type: ConsoleType = ConsoleType.PS4_RPI

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val extendedClient: OkHttpClient = httpClient.newBuilder()
        .readTimeout(maxOf(config.timeoutMs, 15_000L), TimeUnit.MILLISECONDS)
        .writeTimeout(maxOf(config.timeoutMs, 15_000L), TimeUnit.MILLISECONDS)
        .build()

    private val baseUrl = "http://${config.host}:${config.httpPort}"

    override suspend fun checkConnection(): ConsoleHealth = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val response = post("/api/is_exists", buildJsonObject { put("title_id", "CUSA000000") })
        val elapsed = System.currentTimeMillis() - started
        if (response.json != null) {
            ConsoleHealth(
                reachable = true,
                message = "PS4 RPI accesible en ${config.host}:${config.httpPort}",
                latencyMs = elapsed,
            )
        } else {
            ConsoleHealth(
                reachable = false,
                message = response.error ?: "Sin respuesta de flatZ RPI en ${config.host}:${config.httpPort}",
            )
        }
    }

    override suspend fun install(
        pkg: PkgFile,
        pkgUrl: String,
        iconUrl: String?,
    ): ConsoleInstallResult = withContext(Dispatchers.IO) {
        val response = post(
            path = "/api/install",
            body = buildJsonObject {
                put("type", "direct")
                put(
                    "packages",
                    kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive(pkgUrl))
                    },
                )
            },
            extendedTimeout = true,
        )

        val json = response.json
            ?: return@withContext ConsoleInstallResult.Failure(
                response.error ?: "La PS4 no respondió a la solicitud de instalación",
            )

        val status = json.stringValue("status")
        if (status == "success") {
            val taskId = json.longValue("task_id")
            logger.info("PS4 RPI", "Instalación aceptada, tarea ${taskId ?: "sin id"}")
            return@withContext ConsoleInstallResult.Accepted(taskId)
        }

        val code = json.longValue("error_code")
        if (code == 2157510677L || code == 2157510663L) {
            return@withContext ConsoleInstallResult.AlreadyInstalled(consoleErrorMessage(code))
        }

        ConsoleInstallResult.Failure(consoleErrorMessage(code), code)
    }

    override suspend fun taskProgress(taskId: Long): TaskProgress = withContext(Dispatchers.IO) {
        val response = post("/api/get_task_progress", buildJsonObject { put("task_id", taskId) })
        val json = response.json
            ?: return@withContext TaskProgress.Failure(
                response.error ?: "No se pudo consultar el progreso",
            )

        if (json.stringValue("status") != "success") {
            val code = json.longValue("error_code")
            return@withContext if (code == 2157510681L) {
                TaskProgress.TaskMissing(consoleErrorMessage(code))
            } else {
                TaskProgress.Failure(consoleErrorMessage(code), code)
            }
        }

        val length = json.longValue("length") ?: 0L
        val transferred = json.longValue("transferred") ?: 0L
        val lengthTotal = json.longValue("length_total") ?: 0L
        val transferredTotal = json.longValue("transferred_total") ?: 0L
        val restSeconds = json.longValue("rest_sec_total")
            ?: json.longValue("rest_sec")
            ?: 0L
        val preparing = json.longValue("preparing_percent") ?: 0L
        val localCopy = json.longValue("local_copy_percent") ?: 0L

        val totalBytes = maxOf(lengthTotal, length, 0L)
        val doneBytes = maxOf(transferredTotal, transferred, 0L)
            .coerceAtMost(if (totalBytes > 0L) totalBytes else Long.MAX_VALUE)
        val completed = totalBytes > 0L && doneBytes >= totalBytes

        if (completed) {
            return@withContext TaskProgress.Completed
        }

        val percent = if (totalBytes > 0L) {
            ((doneBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }

        TaskProgress.Running(
            percent = percent,
            restSeconds = restSeconds.coerceAtLeast(0L),
            transferredBytes = doneBytes,
            totalBytes = totalBytes,
            detail = "prep ${preparing}% · copia ${localCopy}% · " +
                "$doneBytes/$totalBytes bytes · rest ${restSeconds}s",
        )
    }

    override suspend fun pause(taskId: Long): Boolean = simpleCommand("/api/pause_task", taskId)

    override suspend fun stop(taskId: Long): Boolean = simpleCommand("/api/stop_task", taskId)

    override suspend fun resume(taskId: Long): Boolean = simpleCommand("/api/resume_task", taskId)

    override suspend fun unregister(taskId: Long): Boolean =
        simpleCommand("/api/unregister_task", taskId)

    private suspend fun simpleCommand(path: String, taskId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val response = post(path, buildJsonObject { put("task_id", taskId) })
            response.json?.stringValue("status") == "success"
        }

    private fun post(
        path: String,
        body: JsonObject,
        extendedTimeout: Boolean = false,
    ): ApiResponse {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .build()

        return try {
            val client = if (extendedTimeout) extendedClient else httpClient
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                ApiResponse(
                    httpCode = response.code,
                    json = parseJsonObject(raw),
                    raw = raw,
                )
            }
        } catch (exception: Exception) {
            logger.debug("PS4 RPI", "Error en $path: ${exception.message}")
            ApiResponse(
                httpCode = 0,
                json = null,
                raw = "",
                error = when {
                    exception.message?.contains("timeout", ignoreCase = true) == true ->
                        "Tiempo de espera agotado con ${config.host}:${config.httpPort}"

                    else -> "No se pudo conectar con la PS4: ${exception.message}"
                },
            )
        }
    }

    private data class ApiResponse(
        val httpCode: Int,
        val json: JsonObject?,
        val raw: String,
        val error: String? = null,
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
