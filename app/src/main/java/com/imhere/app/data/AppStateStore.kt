package com.imhere.app.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.imhere.app.data.model.AppState
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Serializes the whole application state into a single JSON document.
 *
 * Unknown keys are ignored so a newer build can write additional fields without
 * breaking an older one, and a structurally broken file is replaced by defaults
 * instead of crashing the app.
 */
object AppStateSerializer : Serializer<AppState> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    override val defaultValue: AppState = AppState()

    override suspend fun readFrom(input: InputStream): AppState {
        val bytes = try {
            input.readBytes()
        } catch (exception: Exception) {
            throw CorruptionException("Cannot read the application state", exception)
        }
        if (bytes.isEmpty()) return defaultValue
        return try {
            json.decodeFromString(AppState.serializer(), bytes.decodeToString()).sanitized()
        } catch (exception: SerializationException) {
            throw CorruptionException("Cannot parse the application state", exception)
        } catch (exception: IllegalArgumentException) {
            throw CorruptionException("Cannot parse the application state", exception)
        }
    }

    override suspend fun writeTo(t: AppState, output: OutputStream) {
        output.write(json.encodeToString(AppState.serializer(), t).encodeToByteArray())
        output.flush()
    }
}

object AppStateStore {

    private const val FILE_NAME = "app_state.json"

    fun create(
        context: android.content.Context,
        scope: CoroutineScope,
    ): DataStore<AppState> {
        val directory = java.io.File(context.filesDir, "datastore").apply { mkdirs() }
        val dataStoreFile = java.io.File(directory, FILE_NAME)
        return DataStoreFactory.create(
            serializer = AppStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { AppState() },
            scope = scope,
            produceFile = { dataStoreFile },
        )
    }
}
