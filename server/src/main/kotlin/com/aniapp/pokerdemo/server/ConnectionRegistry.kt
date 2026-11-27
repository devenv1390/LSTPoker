package com.aniapp.pokerdemo.server

import com.aniapp.pokerdemo.net.PokerJson
import com.aniapp.pokerdemo.net.ServerMessage
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.Frame
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Serializable
data class OtaPushRequest(
    val campaignId: String,
    val targetVersionCode: Int,
    val targetVersionName: String,
    val sha256: String
)

@Serializable
data class OtaPushResult(
    val matched: Int,
    val sent: Int,
    val failed: Int,
    val otaConnections: Int
)

class ConnectionRegistry {
    private class Entry(val session: WebSocketServerSession)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val broadcastLock = Mutex()

    @Volatile
    var lastPush: OtaPushResult? = null
        private set

    fun add(session: WebSocketServerSession): String {
        val id = UUID.randomUUID().toString()
        entries[id] = Entry(session)
        return id
    }

    fun remove(id: String) {
        entries.remove(id)
    }

    fun count(): Int = entries.size

    suspend fun broadcast(request: OtaPushRequest): OtaPushResult = broadcastLock.withLock {
        val message = ServerMessage.Error(
            code = "update_available",
            message = "发现新版本 v${request.targetVersionName}",
            updateId = request.campaignId,
            targetVersionCode = request.targetVersionCode
        )
        val json = PokerJson.encodeToString<ServerMessage>(message)
        val snapshot = entries.values.toList()
        var sent = 0
        snapshot.forEach { entry ->
            try {
                // OTA 连接不发送游戏帧，不存在与房间广播并发写入同一会话的情况。
                entry.session.send(Frame.Text(json))
                sent++
            } catch (_: Exception) {
                entries.entries.removeIf { it.value === entry }
            }
        }
        return OtaPushResult(snapshot.size, sent, snapshot.size - sent, count()).also { lastPush = it }
    }
}
