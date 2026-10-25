package com.remotesend.app.data.server

import com.remotesend.app.core.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * Small HTTP/1.1 server used by the console to download PKG files.
 * It supports HEAD, byte ranges and the /hb heartbeat used by the desktop app.
 */
class PkgHttpServer(
    private val port: Int,
    private val provider: HttpContentProvider,
    private val logger: AppLogger,
    private val scope: CoroutineScope,
    private val serverName: String = "RemoteSend",
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val clients = ConcurrentHashMap.newKeySet<Socket>()

    val boundPort: Int
        get() = serverSocket?.localPort ?: port

    @Synchronized
    fun start(): Int {
        check(serverSocket == null) { "El servidor ya está en ejecución" }
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress("0.0.0.0", port))
        serverSocket = socket

        acceptJob = scope.launch(Dispatchers.IO) {
            coroutineScope {
                while (isActive && !socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (_: IOException) {
                        break
                    }
                    clients += client
                    launch {
                        try {
                            handleClient(client)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (exception: IOException) {
                            logger.debug(
                                "Servidor",
                                "Conexión interrumpida por el cliente: ${exception.message}",
                            )
                        } catch (exception: Exception) {
                            logger.error(
                                "Servidor",
                                "Error atendiendo una conexión HTTP",
                                exception.stackTraceToString(),
                            )
                        } finally {
                            clients -= client
                            runCatching { client.close() }
                        }
                    }
                }
            }
        }

        return socket.localPort
    }

    @Synchronized
    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        acceptJob?.cancel()
        acceptJob = null
    }

    private suspend fun handleClient(client: Socket) {
        client.soTimeout = CLIENT_TIMEOUT_MS
        val input = client.getInputStream()
        val output = client.getOutputStream()

        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 3) {
            respondText(output, 400, "Bad Request", "Solicitud inválida")
            return
        }

        val method = parts[0].uppercase(Locale.US)
        val target = parts[1]
        val headers = readHeaders(input)

        if (method == "OPTIONS") {
            writeHeaders(output, 204, "No Content", emptyMap())
            return
        }

        if (method != "GET" && method != "HEAD") {
            respondText(output, 405, "Method Not Allowed", "Método no permitido")
            return
        }

        val rawPath = target.substringBefore('?')
        val path = runCatching {
            URLDecoder.decode(rawPath, "UTF-8")
        }.getOrDefault(rawPath)

        if (path == "/hb" || path == "/heartbeat") {
            handleHeartbeat(output, client, headers, method)
            return
        }

        if (path == "/api/packages") {
            handlePackagesJson(output, client, headers, method)
            return
        }

        if (path == "/" || path == "/index.html") {
            handleIndex(output, client, headers, method)
            return
        }

        val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
        if (segments.size >= 2 && segments[0] == "pkg") {
            handlePackage(output, headers, method, segments[1])
            return
        }

        if (segments.size >= 2 && segments[0] == "icon") {
            handleIcon(output, method, segments[1])
            return
        }

        respondText(output, 404, "Not Found", "Ruta no encontrada")
    }

    private suspend fun handleHeartbeat(
        output: OutputStream,
        client: Socket,
        headers: Map<String, String>,
        method: String,
    ) {
        val packages = provider.packages()
        val json = buildJsonObject {
            put("status", "ok")
            put("app", serverName)
            put("message", "RemoteSend está sirviendo archivos PKG")
            put("packages", packages.size)
            put("remoteAddress", client.inetAddress.hostAddress ?: "")
            put("remotePort", client.port)
            put("localAddress", client.localAddress.hostAddress ?: "")
            put("localPort", client.localPort)
            put("time", System.currentTimeMillis())
        }
        respondJson(output, 200, "OK", json, method == "HEAD")
    }

    private suspend fun handlePackagesJson(
        output: OutputStream,
        client: Socket,
        headers: Map<String, String>,
        method: String,
    ) {
        val packages = provider.packages()
        val base = requestBaseUrl(client, headers)
        val array = buildJsonArray {
            packages.forEach { pkg ->
                add(
                    buildJsonObject {
                        put("id", pkg.id)
                        put("name", pkg.displayName)
                        put("safeName", pkg.safeName)
                        put("title", pkg.title ?: "")
                        put("titleId", pkg.titleId ?: "")
                        put("size", pkg.sizeBytes)
                        put("url", "$base/pkg/${pkg.id}/${pkg.safeName}")
                        put("iconUrl", if (pkg.hasIcon) "$base/icon/${pkg.id}" else "")
                    },
                )
            }
        }
        val json = buildJsonObject {
            put("status", "ok")
            put("count", packages.size)
            put("packages", array)
        }
        respondJson(output, 200, "OK", json, method == "HEAD")
    }

    private suspend fun handleIndex(
        output: OutputStream,
        client: Socket,
        headers: Map<String, String>,
        method: String,
    ) {
        val packages = provider.packages()
        val base = requestBaseUrl(client, headers)
        val rows = packages.joinToString("\n") { pkg ->
            val title = escapeHtml(pkg.title ?: pkg.displayName)
            val url = "$base/pkg/${pkg.id}/${pkg.safeName}"
            "<tr><td>${escapeHtml(pkg.displayName)}</td><td>${escapeHtml(pkg.titleId ?: "-")}</td>" +
                "<td>${pkg.sizeBytes}</td><td><a href=\"$url\">$title</a></td></tr>"
        }
        val html = """
            <!doctype html>
            <html lang="es">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>RemoteSend</title>
              <style>
                body{font-family:system-ui,sans-serif;margin:24px;background:#f7f9fc;color:#191c20}
                h1{color:#0070d1}table{border-collapse:collapse;width:100%}
                th,td{padding:8px;border-bottom:1px solid #dfe2eb;text-align:left}
                a{color:#0070d1}
              </style>
            </head>
            <body>
              <h1>RemoteSend</h1>
              <p>Servidor de paquetes activo. ${packages.size} archivo(s) disponibles.</p>
              <p><a href="/hb">Heartbeat</a> · <a href="/api/packages">JSON</a></p>
              <table>
                <thead><tr><th>Archivo</th><th>Title ID</th><th>Bytes</th><th>PKG</th></tr></thead>
                <tbody>$rows</tbody>
              </table>
            </body>
            </html>
        """.trimIndent()
        respondText(output, 200, "OK", html, "text/html; charset=utf-8", method == "HEAD")
    }

    private suspend fun handlePackage(
        output: OutputStream,
        headers: Map<String, String>,
        method: String,
        packageId: String,
    ) {
        val pkg = provider.packages().firstOrNull { it.id == packageId }
        if (pkg == null) {
            respondJson(
                output,
                404,
                "Not Found",
                buildJsonObject {
                    put("status", "fail")
                    put("error", "PKG no encontrado")
                },
                method == "HEAD",
            )
            return
        }

        val size = pkg.sizeBytes.coerceAtLeast(0L)
        val range = RangeHeader.parse(headers["range"], size)
        val start: Long
        val length: Long
        val status: Int
        val reason: String

        when (range) {
            is RangeResult.Valid -> {
                start = range.start
                length = range.length
                status = 206
                reason = "Partial Content"
            }

            RangeResult.Unsatisfiable -> {
                writeHeaders(
                    output,
                    416,
                    "Range Not Satisfiable",
                    mapOf("Content-Range" to "bytes */$size"),
                )
                return
            }

            RangeResult.Ignore -> {
                start = 0L
                length = size
                status = 200
                reason = "OK"
            }
        }

        val responseHeaders = linkedMapOf(
            "Content-Type" to "application/octet-stream",
            "Content-Length" to length.toString(),
            "Accept-Ranges" to "bytes",
            "Cache-Control" to "no-store",
            "Content-Disposition" to "attachment; filename=\"${pkg.safeName}\"",
        )
        if (status == 206) {
            responseHeaders["Content-Range"] = "bytes $start-${start + length - 1}/$size"
        }

        writeHeaders(output, status, reason, responseHeaders)
        if (method == "HEAD" || length == 0L) return

        val stream = provider.openStream(packageId, start)
        if (stream == null) {
            logger.error("Servidor", "No se pudo abrir ${pkg.displayName}")
            return
        }

        stream.use { input ->
            copyRange(input, output, length)
        }
        output.flush()
    }

    private suspend fun handleIcon(
        output: OutputStream,
        method: String,
        packageId: String,
    ) {
        val bytes = provider.icon(packageId)
        if (bytes == null || bytes.isEmpty()) {
            respondText(output, 404, "Not Found", "Icono no disponible")
            return
        }
        val headers = linkedMapOf(
            "Content-Type" to "image/png",
            "Content-Length" to bytes.size.toString(),
            "Cache-Control" to "public, max-age=86400",
        )
        writeHeaders(output, 200, "OK", headers)
        if (method != "HEAD") output.write(bytes)
        output.flush()
    }

    private fun copyRange(input: InputStream, output: OutputStream, length: Long) {
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = length
        while (remaining > 0) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read < 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun respondJson(
        output: OutputStream,
        code: Int,
        reason: String,
        json: JsonObject,
        headOnly: Boolean,
    ) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val headers = linkedMapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "Content-Length" to bytes.size.toString(),
            "Cache-Control" to "no-store",
        )
        writeHeaders(output, code, reason, headers)
        if (!headOnly) output.write(bytes)
        output.flush()
    }

    private fun respondText(
        output: OutputStream,
        code: Int,
        reason: String,
        text: String,
        contentType: String = "text/plain; charset=utf-8",
        headOnly: Boolean = false,
    ) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val headers = linkedMapOf(
            "Content-Type" to contentType,
            "Content-Length" to bytes.size.toString(),
            "Cache-Control" to "no-store",
        )
        writeHeaders(output, code, reason, headers)
        if (!headOnly) output.write(bytes)
        output.flush()
    }

    private fun writeHeaders(
        output: OutputStream,
        code: Int,
        reason: String,
        headers: Map<String, String>,
    ) {
        val builder = StringBuilder()
        builder.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
        builder.append("Date: ").append(httpDate()).append("\r\n")
        builder.append("Server: ").append(serverName).append("\r\n")
        builder.append("Access-Control-Allow-Origin: *\r\n")
        builder.append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
        builder.append("Access-Control-Allow-Headers: Range, Content-Type\r\n")
        headers.forEach { (name, value) ->
            builder.append(name).append(": ").append(value).append("\r\n")
        }
        builder.append("Connection: close\r\n\r\n")
        output.write(builder.toString().toByteArray(Charsets.US_ASCII))
    }

    private fun readHeaders(input: InputStream): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val name = line.substring(0, separator).trim().lowercase(Locale.US)
            val value = line.substring(separator + 1).trim()
            headers[name] = value
            if (headers.size >= MAX_HEADERS) break
        }
        return headers
    }

    private fun readLine(input: InputStream): String? {
        val builder = StringBuilder()
        var previous = -1
        while (true) {
            val value = input.read()
            if (value < 0) {
                return if (builder.isEmpty()) null else builder.toString()
            }
            if (previous == '\r'.code && value == '\n'.code) {
                if (builder.isNotEmpty()) builder.setLength(builder.length - 1)
                return builder.toString()
            }
            builder.append(value.toChar())
            previous = value
            if (builder.length > MAX_LINE_LENGTH) return null
        }
    }

    private fun requestBaseUrl(client: Socket, headers: Map<String, String>): String {
        val host = headers["host"]?.takeIf { it.isNotBlank() }
            ?: "${client.localAddress.hostAddress}:${client.localPort}"
        return "http://$host"
    }

    private fun httpDate(): String {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        format.timeZone = TimeZone.getTimeZone("GMT")
        return format.format(Date())
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    companion object {
        private const val CLIENT_TIMEOUT_MS = 20_000
        private const val MAX_HEADERS = 64
        private const val MAX_LINE_LENGTH = 8_192
        private const val BUFFER_SIZE = 64 * 1024
    }
}
