package com.aniapp.pokerdemo.server

import com.aniapp.pokerdemo.net.ClientMessage
import com.aniapp.pokerdemo.net.PokerJson
import com.aniapp.pokerdemo.net.ServerMessage
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.server.cio.CIO
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

fun main() {
    embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module(aiDelayMs: LongRange = 700L..1400L) {
    install(WebSockets) {
        pingPeriodMillis = 15_000
        timeoutMillis = 30_000
    }
    install(ContentNegotiation) { json(PokerJson) }
    val manager = RoomManager(aiDelayMs)
    val startedAt = System.currentTimeMillis()
    routing {
        get("/status") {
            // cloudflared 从 loopback 连入，公网流量也呈本机地址，但必带 CF 头
            val remote = call.request.local.remoteAddress
            val proxied = call.request.headers["Cf-Ray"] != null ||
                call.request.headers["Cf-Connecting-Ip"] != null ||
                call.request.headers["X-Forwarded-For"] != null
            val loopback = remote == "127.0.0.1" || remote == "::1" || remote == "0:0:0:0:0:0:0:1"
            if (!loopback || proxied) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(StatusPayload((System.currentTimeMillis() - startedAt) / 1000, manager.snapshotAll()))
        }
        webSocket("/ws") {
            var room: Room? = null
            var mySeat = -1

            suspend fun reply(msg: ServerMessage) {
                send(Frame.Text(PokerJson.encodeToString<ServerMessage>(msg)))
            }

            suspend fun join(r: Room, nickname: String) {
                try {
                    val seat = r.addHuman(nickname, this)
                    room = r
                    mySeat = seat
                    reply(ServerMessage.Joined(r.code, seat, r.seatCount))
                    r.broadcastLobby()
                } catch (ex: RoomException) {
                    reply(ServerMessage.Error("join_failed", ex.message ?: "加入失败"))
                }
            }

            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val msg = try {
                        PokerJson.decodeFromString<ClientMessage>(frame.readText())
                    } catch (_: Exception) {
                        reply(ServerMessage.Error("bad_message", "无法解析的消息"))
                        continue
                    }
                    when (msg) {
                        is ClientMessage.CreateRoom -> {
                            if (room != null) {
                                reply(ServerMessage.Error("in_room", "已在房间中"))
                                continue
                            }
                            if (msg.seatCount !in 2..6) {
                                reply(ServerMessage.Error("bad_seat_count", "座位数必须为 2-6"))
                                continue
                            }
                            join(manager.create(msg.seatCount, msg.aiFill), cleanName(msg.nickname))
                        }
                        is ClientMessage.JoinRoom -> {
                            if (room != null) {
                                reply(ServerMessage.Error("in_room", "已在房间中"))
                                continue
                            }
                            val r = manager.get(msg.code)
                            if (r == null) {
                                reply(ServerMessage.Error("not_found", "房间不存在"))
                                continue
                            }
                            join(r, cleanName(msg.nickname))
                        }
                        ClientMessage.StartHand -> {
                            val r = room
                            if (r == null) reply(ServerMessage.Error("no_room", "尚未加入房间"))
                            else r.requestStart(mySeat)
                        }
                        is ClientMessage.Act -> {
                            val r = room
                            if (r == null) reply(ServerMessage.Error("no_room", "尚未加入房间"))
                            else r.requestAct(mySeat, msg.action)
                        }
                        is ClientMessage.SetAiFill -> {
                            val r = room
                            if (r == null) reply(ServerMessage.Error("no_room", "尚未加入房间"))
                            else r.setAiFill(mySeat, msg.enabled)
                        }
                        ClientMessage.Leave -> {
                            room?.disconnected(mySeat)
                            room = null
                            mySeat = -1
                        }
                    }
                }
            } finally {
                room?.disconnected(mySeat)
            }
        }
    }
}

private fun cleanName(nickname: String): String =
    nickname.trim().take(12).ifEmpty { "玩家" }

@Serializable
data class StatusPayload(val uptimeSec: Long, val rooms: List<RoomSnapshot>)