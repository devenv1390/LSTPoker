package com.aniapp.pokerdemo.update

import android.util.Log
import com.aniapp.pokerdemo.net.PokerJson
import com.aniapp.pokerdemo.net.ServerMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString

class OtaUpdateClient(
    private val scope: CoroutineScope,
    private val versionCode: Long,
    private val onUpdateAvailable: (ServerMessage.Error) -> Unit
) {
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 15_000
        }
    }
    private val lifecycleLock = Mutex()
    private val mutableConnected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()

    @Volatile
    private var closed = false
    private var endpoint: UpdateEndpoint? = null
    private var worker: Job? = null

    fun reconfigure(newEndpoint: UpdateEndpoint?) {
        scope.launch {
            lifecycleLock.withLock {
                if (closed || endpoint == newEndpoint) return@withLock
                endpoint = newEndpoint
                worker?.cancelAndJoin()
                worker = null
                if (newEndpoint != null) worker = scope.launch { run(newEndpoint) }
            }
        }
    }

    suspend fun stop() {
        lifecycleLock.withLock {
            endpoint = null
            worker?.cancelAndJoin()
            worker = null
            mutableConnected.value = false
        }
    }

    suspend fun close() {
        lifecycleLock.withLock {
            closed = true
            endpoint = null
            worker?.cancelAndJoin()
            worker = null
            mutableConnected.value = false
        }
        client.close()
    }

    private suspend fun run(target: UpdateEndpoint) {
        var attempt = 0
        while (scope.isActive) {
            try {
                val session = client.webSocketSession(target.gameWebSocketUrl(versionCode))
                try {
                    mutableConnected.value = true
                    attempt = 0
                    consume(session)
                } finally {
                    mutableConnected.value = false
                    runCatching { session.close() }
                }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: Exception) {
                Log.w(TAG, "OTA WebSocket disconnected: ${error.message}")
            } finally {
                mutableConnected.value = false
            }
            delay(reconnectDelay(attempt++))
        }
    }

    private suspend fun consume(session: DefaultClientWebSocketSession) {
        for (frame in session.incoming) {
            if (frame !is Frame.Text) continue
            val message = runCatching {
                PokerJson.decodeFromString<ServerMessage>(frame.readText())
            }.getOrNull() ?: continue
            if (message is ServerMessage.Error && message.code == CODE_UPDATE_AVAILABLE) {
                onUpdateAvailable(message)
            }
        }
    }

    private fun reconnectDelay(attempt: Int): Long {
        val exponential = INITIAL_RECONNECT_MS * (1L shl attempt.coerceAtMost(5))
        val capped = exponential.coerceAtMost(MAX_RECONNECT_MS)
        val jitter = Random.nextDouble(0.8, 1.2)
        return (capped * jitter).toLong().coerceAtLeast(1L)
    }

    private companion object {
        const val TAG = "PokerOtaClient"
        const val CODE_UPDATE_AVAILABLE = "update_available"
        const val INITIAL_RECONNECT_MS = 1_000L
        const val MAX_RECONNECT_MS = 30_000L
    }
}
