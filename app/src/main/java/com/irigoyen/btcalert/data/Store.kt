package com.irigoyen.btcalert.data

import android.content.Context
import com.irigoyen.btcalert.model.AppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Single-file JSON persistence. Small enough (a few thousand price samples at most)
 * that a database would be more code than it's worth. All writes go through [update]
 * under a mutex so the worker, the service and the UI never clobber each other.
 */
class Store private constructor(context: Context) {
    private val file = File(context.filesDir, "state.json")
    private val tmp = File(context.filesDir, "state.json.tmp")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val mutex = Mutex()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state

    private fun load(): AppState = try {
        if (file.exists()) json.decodeFromString<AppState>(file.readText()) else AppState()
    } catch (e: Exception) {
        // Not a fetch failure, so it doesn't belong in the price hero — the log screen is where
        // someone would go looking after their rules vanished.
        AppState(log = listOf("State file unreadable, started fresh: ${e.message}"))
    }

    suspend fun update(block: (AppState) -> AppState): AppState = mutex.withLock {
        val next = block(_state.value)
        tmp.writeText(json.encodeToString(next))
        if (!tmp.renameTo(file)) { file.writeText(json.encodeToString(next)); tmp.delete() }
        _state.value = next
        next
    }

    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(context.applicationContext).also { instance = it }
        }
    }
}
