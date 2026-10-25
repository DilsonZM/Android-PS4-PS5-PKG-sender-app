package com.remotesend.app.data.network

import com.remotesend.app.core.AppLogger
import com.remotesend.app.domain.model.ConsoleConfig
import com.remotesend.app.domain.model.ConsoleConnectionState
import com.remotesend.app.domain.model.ConsoleConnectionStatus
import com.remotesend.app.domain.model.ConsoleType
import com.remotesend.app.domain.model.DetectionResult
import com.remotesend.app.domain.model.isTcpBased
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ConsoleDetector(
    private val factory: ConsoleClientFactory,
    private val logger: AppLogger,
) {
    companion object {
        const val RPI_PORT_FLATZ = 12800
        const val RPI_PORT_PS4RPI = 12801
        const val TCP_PORT = 9090
    }

    private val _connection = MutableStateFlow(ConsoleConnectionState())
    val connection: StateFlow<ConsoleConnectionState> = _connection.asStateFlow()

    suspend fun detect(config: ConsoleConfig): DetectionResult = withContext(Dispatchers.IO) {
        _connection.value = ConsoleConnectionState(
            status = ConsoleConnectionStatus.CHECKING,
            type = config.type,
            message = "Comprobando ${config.host.ifBlank { "la consola" }}…",
        )
        val result = runDetection(config)
        _connection.value = ConsoleConnectionState(
            status = if (result.reachable) {
                ConsoleConnectionStatus.CONNECTED
            } else {
                ConsoleConnectionStatus.DISCONNECTED
            },
            type = result.type,
            message = result.message,
            latencyMs = result.latencyMs,
            ambiguousTcp = result.ambiguousTcp,
            checkedAt = System.currentTimeMillis(),
        )
        result
    }

    private suspend fun runDetection(config: ConsoleConfig): DetectionResult {
        if (config.host.isBlank()) {
            return DetectionResult(
                reachable = false,
                message = "Configura la IP de la consola primero",
            )
        }

        if (config.type != ConsoleType.AUTO) {
            val health = factory.create(config.type, config).checkConnection()
            return DetectionResult(
                reachable = health.reachable,
                type = config.type,
                message = health.message,
                latencyMs = health.latencyMs,
                ambiguousTcp = config.type.isTcpBased,
                httpPort = if (config.type == ConsoleType.PS4_RPI) config.httpPort else null,
                tcpPort = if (config.type.isTcpBased) config.tcpPort else null,
            )
        }

        val rpiPorts = listOf(config.httpPort, RPI_PORT_PS4RPI, RPI_PORT_FLATZ)
            .distinct()
        var lastRpiHealth: com.remotesend.app.domain.model.ConsoleHealth? = null
        for (port in rpiPorts) {
            val health = factory.create(
                ConsoleType.PS4_RPI,
                config.copy(httpPort = port),
            ).checkConnection()
            lastRpiHealth = health
            if (health.reachable) {
                logger.success(
                    "Detección",
                    "PS4 RPI detectada en ${config.host}:$port",
                )
                return DetectionResult(
                    reachable = true,
                    type = ConsoleType.PS4_RPI,
                    message = "PS4 RPI accesible en ${config.host}:$port" +
                        " (flatZ 12800 / PS4RPI 12801)",
                    latencyMs = health.latencyMs,
                    httpPort = port,
                )
            }
        }

        val tcpType = if (config.preferPs5ForTcp) {
            ConsoleType.PS5_ETAHEN
        } else {
            ConsoleType.PS4_GOLDHEN
        }
        val tcpPorts = listOf(config.tcpPort, TCP_PORT).distinct()
        var lastTcpHealth: com.remotesend.app.domain.model.ConsoleHealth? = null
        for (port in tcpPorts) {
            val health = factory.create(
                tcpType,
                config.copy(tcpPort = port),
            ).checkConnection()
            lastTcpHealth = health
            if (health.reachable) {
                logger.success(
                    "Detección",
                    "Socket RPI detectado en ${config.host}:$port",
                )
                return DetectionResult(
                    reachable = true,
                    type = tcpType,
                    message = "Puerto $port abierto. Se asumió ${
                        if (tcpType == ConsoleType.PS5_ETAHEN) "PS5 etaHEN" else "PS4 GoldHEN"
                    }; cámbialo manualmente si no es correcto.",
                    latencyMs = health.latencyMs,
                    ambiguousTcp = true,
                    tcpPort = port,
                )
            }
        }

        return DetectionResult(
            reachable = false,
            message = "No se detectó la consola. RPI: ${lastRpiHealth?.message}. " +
                "TCP: ${lastTcpHealth?.message}",
        )
    }
}
