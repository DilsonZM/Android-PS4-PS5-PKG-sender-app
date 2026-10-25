package com.remotesend.app.data.network

import com.remotesend.app.core.AppLogger
import com.remotesend.app.domain.model.ConsoleConfig
import com.remotesend.app.domain.model.ConsoleType

class ConsoleClientFactory(
    private val logger: AppLogger,
) {
    fun createRpi(config: ConsoleConfig): Ps4RpiClient = Ps4RpiClient(config, logger)

    fun create(type: ConsoleType, config: ConsoleConfig): ConsoleClient {
        return when (type) {
            ConsoleType.PS4_RPI -> Ps4RpiClient(config, logger)
            ConsoleType.PS4_GOLDHEN, ConsoleType.PS5_ETAHEN ->
                TcpConsoleClient(config, type, logger)

            ConsoleType.AUTO -> throw IllegalArgumentException(
                "El tipo de consola debe resolverse antes de crear el cliente",
            )
        }
    }
}
