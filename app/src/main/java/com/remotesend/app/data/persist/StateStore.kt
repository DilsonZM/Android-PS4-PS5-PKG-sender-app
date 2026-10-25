package com.remotesend.app.data.persist

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.remotesend.app.domain.model.PersistedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.IOException

private val persistJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

private val Context.remoteSendDataStore: DataStore<PersistedState> by dataStore(
    fileName = "remotesend_state.json",
    serializer = PersistedStateSerializer(persistJson),
)

class StateStore(context: Context) {

    private val dataStore = context.applicationContext.remoteSendDataStore

    val state: Flow<PersistedState> = dataStore.data.catch { throwable ->
        if (throwable is IOException) emit(PersistedState()) else throw throwable
    }

    suspend fun snapshot(): PersistedState = state.first()

    suspend fun update(transform: (PersistedState) -> PersistedState): PersistedState {
        return dataStore.updateData { current ->
            transform(current)
        }
    }
}
