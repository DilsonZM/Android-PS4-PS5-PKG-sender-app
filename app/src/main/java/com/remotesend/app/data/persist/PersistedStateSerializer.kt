package com.remotesend.app.data.persist

import androidx.datastore.core.Serializer
import com.remotesend.app.domain.model.PersistedState
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

class PersistedStateSerializer(
    private val json: Json,
) : Serializer<PersistedState> {

    override val defaultValue: PersistedState = PersistedState()

    override suspend fun readFrom(input: InputStream): PersistedState {
        return try {
            val content = input.readBytes().decodeToString()
            if (content.isBlank()) defaultValue
            else json.decodeFromString<PersistedState>(content)
        } catch (_: Exception) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: PersistedState, output: OutputStream) {
        output.write(json.encodeToString(PersistedState.serializer(), t).encodeToByteArray())
    }
}
