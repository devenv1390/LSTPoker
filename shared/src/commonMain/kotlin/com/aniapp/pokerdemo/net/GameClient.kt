package com.aniapp.pokerdemo.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class GameClient(private val scope: CoroutineScope) {
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 15_000
        }
    }
    private var session: DefaultClientWebSocketSession? = null

    private val _incoming = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    val incoming: SharedFlow<ServerMessage> = _incoming

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    suspend fun connect(host: String, port: Int, secure: Boolean = false) {
        val scheme = if (secure) "wss" else "ws"
        val s = client.webSocketSession("$scheme://$host:$port/ws")
        session = s
        _connected.value = true
        scope.launch {
            try {
                for (frame in s.incoming) {
                    if (frame is Frame.Text) {
                        _incoming.emit(PokerJson.decodeFromString<ServerMessage>(frame.readText()))
                    }
                }
            } catch (_: Exception) {
            } finally {
                _connected.value = false
            }
        }
    }

    suspend fun send(message: ClientMessage) {
        session?.send(Frame.Text(PokerJson.encodeToString<ClientMessage>(message)))
    }

    suspend fun close() {
        session?.close()
        session = null
        _connected.value = false
        client.close()
    }
}