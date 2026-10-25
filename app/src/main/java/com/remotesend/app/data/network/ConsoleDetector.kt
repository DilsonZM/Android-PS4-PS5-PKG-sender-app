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
            )
        }

        val rpiHealth = factory.create(ConsoleType.PS4_RPI, config).checkConnection()
        if (rpiHealth.reachable) {
            logger.success(
                "Detección",
                "PS4 con flatZ RPI detectada en ${config.host}:${config.httpPort}",
            )
            return DetectionResult(
                reachable = true,
                type = ConsoleType.PS4_RPI,
                message = "PS4 con flatZ RPI detectada",
                latencyMs = rpiHealth.latencyMs,
            )
        }

        val tcpType = if (config.preferPs5ForTcp) {
            ConsoleType.PS5_ETAHEN
        } else {
            ConsoleType.PS4_GOLDHEN
        }
        val tcpHealth = factory.create(tcpType, config).checkConnection()
        if (tcpHealth.reachable) {
            logger.success(
                "Detección",
                "Socket RPI detectado en ${config.host}:${config.tcpPort}",
            )
            return DetectionResult(
                reachable = true,
                type = tcpType,
                message = "Puerto ${config.tcpPort} abierto. Se asumió ${
                    if (tcpType == ConsoleType.PS5_ETAHEN) "PS5 etaHEN" else "PS4 GoldHEN"
                }; cámbialo manualmente si no es correcto.",
                latencyMs = tcpHealth.latencyMs,
                ambiguousTcp = true,
            )
        }

        return DetectionResult(
            reachable = false,
            message = "No se detectó la consola. RPI: ${rpiHealth.message}. " +
                "TCP: ${tcpHealth.message}",
        )
    }
}
