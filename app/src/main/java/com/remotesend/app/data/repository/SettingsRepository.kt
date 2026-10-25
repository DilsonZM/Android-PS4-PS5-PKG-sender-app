package com.remotesend.app.data.repository

import com.remotesend.app.data.persist.StateStore
import com.remotesend.app.domain.model.AppSettings
import com.remotesend.app.domain.model.ConsoleConfig
import com.remotesend.app.domain.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val stateStore: StateStore,
) {
    val state: Flow<com.remotesend.app.domain.model.PersistedState> = stateStore.state

    val settings: Flow<AppSettings> =
        stateStore.state.map { it.settings }.distinctUntilChanged()

    val console: Flow<ConsoleConfig> =
        stateStore.state.map { it.console }.distinctUntilChanged()

    val server: Flow<ServerConfig> =
        stateStore.state.map { it.server }.distinctUntilChanged()

    suspend fun snapshot(): com.remotesend.app.domain.model.PersistedState = stateStore.snapshot()

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        stateStore.update { it.copy(settings = transform(it.settings)) }
    }

    suspend fun updateConsole(transform: (ConsoleConfig) -> ConsoleConfig) {
        stateStore.update { it.copy(console = transform(it.console)) }
    }

    suspend fun updateServer(transform: (ServerConfig) -> ServerConfig) {
        stateStore.update { it.copy(server = transform(it.server)) }
    }

    suspend fun saveConsole(config: ConsoleConfig) {
        stateStore.update { it.copy(console = config) }
    }

    suspend fun saveServer(config: ServerConfig) {
        stateStore.update { it.copy(server = config) }
    }
}
