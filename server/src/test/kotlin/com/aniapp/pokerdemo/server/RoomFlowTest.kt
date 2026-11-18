package com.aniapp.pokerdemo.server

import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.net.ClientMessage
import com.aniapp.pokerdemo.net.PokerJson
import com.aniapp.pokerdemo.net.ServerMessage
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RoomFlowTest {

    private suspend fun DefaultClientWebSocketSession.sendMsg(msg: ClientMessage) {
        send(Frame.Text(PokerJson.encodeToString<ClientMessage>(msg)))
    }

    private suspend fun DefaultClientWebSocketSession.next(): ServerMessage = withTimeout(15_000) {
        while (true) {
            val frame = incoming.receive()
            if (frame is Frame.Text) {
                return@withTimeout PokerJson.decodeFromString<ServerMessage>(frame.readText())
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private suspend inline fun <reified T : ServerMessage> DefaultClientWebSocketSession.skipTo(): T {
        while (true) {
            val msg = next()
            if (msg is T) return msg
        }
    }

    private fun testApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { module(1L..5L) }
            block()
        }

    @Test
    fun createJoinHiddenInfoAndFold() = testApp {
        val client = createClient { install(WebSockets) }
        val a = client.webSocketSession("/ws")
        a.sendMsg(ClientMessage.CreateRoom("A", 2))
        val joinedA = a.skipTo<ServerMessage.Joined>()
        assertEquals(0, joinedA.mySeat)

        val b = client.webSocketSession("/ws")
        b.sendMsg(ClientMessage.JoinRoom(joinedA.roomCode, "B"))
        val joinedB = b.skipTo<ServerMessage.Joined>()
        assertEquals(1, joinedB.mySeat)
        a.skipTo<ServerMessage.Lobby>()
        b.skipTo<ServerMessage.Lobby>()

        a.sendMsg(ClientMessage.StartHand)
        val ua = a.skipTo<ServerMessage.Update>()
        val ub = b.skipTo<ServerMessage.Update>()
        assertEquals(0, ua.view.mySeat)
        assertEquals(1, ub.view.mySeat)
        assertEquals(2, ua.view.myHole.size)
        assertEquals(2, ub.view.myHole.size)
        assertNotEquals(ua.view.myHole, ub.view.myHole)
        assertTrue(ua.view.revealedHoles.isEmpty())
        assertTrue(ub.view.revealedHoles.isEmpty())
        assertEquals(Phase.PREFLOP, ua.view.phase)
        assertEquals(0, ua.view.acting)
        assertTrue(ua.view.legal.canFold)
        assertTrue(!ub.view.legal.canFold)

        a.sendMsg(ClientMessage.Act(PlayerAction.Fold))
        val fa = a.skipTo<ServerMessage.Update>()
        assertEquals(Phase.HAND_OVER, fa.view.phase)
        assertEquals(1990, fa.view.seats[0].chips)
        assertEquals(2010, fa.view.seats[1].chips)
        b.skipTo<ServerMessage.Update>()
        a.skipTo<ServerMessage.Lobby>()
        b.skipTo<ServerMessage.Lobby>()
        a.close()
        b.close()
    }

    @Test
    fun illegalActionAndNonHostStartReturnError() = testApp {
        val client = createClient { install(WebSockets) }
        val a = client.webSocketSession("/ws")
        a.sendMsg(ClientMessage.CreateRoom("A", 2))
        val joined = a.skipTo<ServerMessage.Joined>()
        val b = client.webSocketSession("/ws")
        b.sendMsg(ClientMessage.JoinRoom(joined.roomCode, "B"))
        b.skipTo<ServerMessage.Joined>()
        a.skipTo<ServerMessage.Lobby>()
        b.skipTo<ServerMessage.Lobby>()

        b.sendMsg(ClientMessage.StartHand)
        assertEquals("not_host", b.skipTo<ServerMessage.Error>().code)

        a.sendMsg(ClientMessage.StartHand)
        a.skipTo<ServerMessage.Update>()
        b.skipTo<ServerMessage.Update>()

        b.sendMsg(ClientMessage.Act(PlayerAction.Fold))
        assertEquals("illegal_action", b.skipTo<ServerMessage.Error>().code)

        a.sendMsg(ClientMessage.Act(PlayerAction.Fold))
        a.skipTo<ServerMessage.Update>()
        a.close()
        b.close()
    }

    @Test
    fun roomFullRejected() = testApp {
        val client = createClient { install(WebSockets) }
        val a = client.webSocketSession("/ws")
        a.sendMsg(ClientMessage.CreateRoom("A", 2))
        val joined = a.skipTo<ServerMessage.Joined>()
        val b = client.webSocketSession("/ws")
        b.sendMsg(ClientMessage.JoinRoom(joined.roomCode, "B"))
        b.skipTo<ServerMessage.Joined>()
        val c = client.webSocketSession("/ws")
        c.sendMsg(ClientMessage.JoinRoom(joined.roomCode, "C"))
        assertEquals("join_failed", c.skipTo<ServerMessage.Error>().code)
        a.close()
        b.close()
        c.close()
    }

    @Test
    fun aiFillsEmptySeatsAndPlaysAutomatically() = testApp {
        val client = createClient { install(WebSockets) }
        val a = client.webSocketSession("/ws")
        a.sendMsg(ClientMessage.CreateRoom("A", 4))
        a.skipTo<ServerMessage.Joined>()
        a.sendMsg(ClientMessage.StartHand)
        var view = a.skipTo<ServerMessage.Update>().view
        assertEquals(4, view.seats.size)
        assertEquals(3, view.seats.count { it.isAi })
        var guard = 0
        while (view.phase.isBetting() && guard++ < 200) {
            if (view.acting == view.mySeat) {
                val action = if (view.legal.canCheck) PlayerAction.Check else PlayerAction.Call
                a.sendMsg(ClientMessage.Act(action))
            }
            view = a.skipTo<ServerMessage.Update>().view
        }
        assertTrue(view.phase == Phase.HAND_OVER || view.phase == Phase.MATCH_OVER)
        assertEquals(5, view.board.size)
        assertTrue(view.revealedHoles.isNotEmpty() || view.result?.reason == com.aniapp.pokerdemo.engine.EndReason.FOLD)
        val lobby = a.skipTo<ServerMessage.Lobby>()
        assertEquals(1, lobby.seats.size)
        a.close()
    }

    @Test
    fun disconnectMidHandBecomesAiThenSeatReleased() = testApp {
        val client = createClient { install(WebSockets) }
        val a = client.webSocketSession("/ws")
        a.sendMsg(ClientMessage.CreateRoom("A", 2))
        val joined = a.skipTo<ServerMessage.Joined>()
        val b = client.webSocketSession("/ws")
        b.sendMsg(ClientMessage.JoinRoom(joined.roomCode, "B"))
        b.skipTo<ServerMessage.Joined>()
        a.skipTo<ServerMessage.Lobby>()
        b.skipTo<ServerMessage.Lobby>()
        a.sendMsg(ClientMessage.StartHand)
        var view = a.skipTo<ServerMessage.Update>().view
        b.skipTo<ServerMessage.Update>()
        b.close()

        var guard = 0
        while (view.phase.isBetting() && guard++ < 200) {
            if (view.acting == view.mySeat) {
                val action = if (view.legal.canCheck) PlayerAction.Check else PlayerAction.Call
                a.sendMsg(ClientMessage.Act(action))
            }
            view = a.skipTo<ServerMessage.Update>().view
        }
        assertTrue(view.phase == Phase.HAND_OVER || view.phase == Phase.MATCH_OVER)
        val lobby = a.skipTo<ServerMessage.Lobby>()
        assertEquals(1, lobby.seats.size)
        assertEquals("A", lobby.seats.single().name)
        a.close()
    }
}