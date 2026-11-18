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
import kotlin.test.assertTrue

class AiFillOptionTest {

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

    private suspend fun DefaultClientWebSocketSession.lobbyWithHumans(n: Int): ServerMessage.Lobby {
        while (true) {
            val lobby = skipTo<ServerMessage.Lobby>()
            if (lobby.seats.count { !it.isAi } == n) return lobby
        }
    }

    private fun testApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { module(1L..5L) }
            block()
        }

    @Test
    fun aiFillOffNeedsTwoHumansAndStartsWithoutAi() = testApp {
        val client = createClient { install(WebSockets) }
        val a = client.webSocketSession("/ws")
        a.sendMsg(ClientMessage.CreateRoom("A", 4, aiFill = false))
        val joined = a.skipTo<ServerMessage.Joined>()
        assertEquals(false, a.skipTo<ServerMessage.Lobby>().aiFill)

        a.sendMsg(ClientMessage.StartHand)
        assertEquals("need_players", a.skipTo<ServerMessage.Error>().code)

        val b = client.webSocketSession("/ws")
        b.sendMsg(ClientMessage.JoinRoom(joined.roomCode, "B"))
        b.skipTo<ServerMessage.Joined>()
        a.lobbyWithHumans(2)
        b.lobbyWithHumans(2)

        a.sendMsg(ClientMessage.StartHand)
        val ua = a.skipTo<ServerMessage.Update>().view
        val ub = b.skipTo<ServerMessage.Update>().view
        assertEquals(2, ua.seats.size)
        assertTrue(ua.seats.none { it.isAi })
        assertEquals(2, ub.seats.size)

        a.sendMsg(ClientMessage.Act(PlayerAction.Fold))
        a.skipTo<ServerMessage.Update>()
        b.skipTo<ServerMessage.Update>()
        val lobby = a.skipTo<ServerMessage.Lobby>()
        assertEquals(2, lobby.seats.size)
        assertEquals(false, lobby.aiFill)
        a.close()
        b.close()
    }

    @Test
    fun onlyHostCanToggleAiFillAndToggleOnFillsSeats() = testApp {
        val client = createClient { install(WebSockets) }
        val a = client.webSocketSession("/ws")
        a.sendMsg(ClientMessage.CreateRoom("A", 4, aiFill = false))
        val joined = a.skipTo<ServerMessage.Joined>()
        val b = client.webSocketSession("/ws")
        b.sendMsg(ClientMessage.JoinRoom(joined.roomCode, "B"))
        b.skipTo<ServerMessage.Joined>()
        a.lobbyWithHumans(2)
        b.lobbyWithHumans(2)

        b.sendMsg(ClientMessage.SetAiFill(true))
        assertEquals("not_host", b.skipTo<ServerMessage.Error>().code)

        a.sendMsg(ClientMessage.SetAiFill(true))
        assertEquals(true, a.lobbyWithHumans(2).aiFill)
        assertEquals(true, b.lobbyWithHumans(2).aiFill)

        a.sendMsg(ClientMessage.StartHand)
        val view = a.skipTo<ServerMessage.Update>().view
        assertEquals(4, view.seats.size)
        assertEquals(2, view.seats.count { it.isAi })
        a.close()
        b.close()
    }

    @Test
    fun aiFillOffKeepsChipsAndDealerAcrossHands() = testApp {
        val client = createClient { install(WebSockets) }
        val a = client.webSocketSession("/ws")
        a.sendMsg(ClientMessage.CreateRoom("A", 2, aiFill = false))
        val joined = a.skipTo<ServerMessage.Joined>()
        val b = client.webSocketSession("/ws")
        b.sendMsg(ClientMessage.JoinRoom(joined.roomCode, "B"))
        b.skipTo<ServerMessage.Joined>()
        a.lobbyWithHumans(2)
        b.lobbyWithHumans(2)

        a.sendMsg(ClientMessage.StartHand)
        var view = a.skipTo<ServerMessage.Update>().view
        b.skipTo<ServerMessage.Update>()
        assertEquals(1, view.handNumber)
        assertEquals(0, view.dealer)
        a.sendMsg(ClientMessage.Act(PlayerAction.Fold))
        view = a.skipTo<ServerMessage.Update>().view
        b.skipTo<ServerMessage.Update>()
        assertEquals(Phase.HAND_OVER, view.phase)
        a.lobbyWithHumans(2)
        b.lobbyWithHumans(2)

        a.sendMsg(ClientMessage.StartHand)
        view = a.skipTo<ServerMessage.Update>().view
        b.skipTo<ServerMessage.Update>()
        assertEquals(2, view.handNumber)
        assertEquals(1, view.dealer)
        assertEquals(1990, view.seats[0].chips + view.seats[0].streetBet)
        assertEquals(2010, view.seats[1].chips + view.seats[1].streetBet)
        a.close()
        b.close()
    }

    @Test
    fun aiFillOffSparseRoomSeatsMapCorrectly() = testApp {
        val client = createClient { install(WebSockets) }
        val a = client.webSocketSession("/ws")
        a.sendMsg(ClientMessage.CreateRoom("A", 4, aiFill = false))
        val joined = a.skipTo<ServerMessage.Joined>()
        val b = client.webSocketSession("/ws")
        b.sendMsg(ClientMessage.JoinRoom(joined.roomCode, "B"))
        b.skipTo<ServerMessage.Joined>()
        val c = client.webSocketSession("/ws")
        c.sendMsg(ClientMessage.JoinRoom(joined.roomCode, "C"))
        c.skipTo<ServerMessage.Joined>()
        a.lobbyWithHumans(3)
        b.lobbyWithHumans(3)
        c.lobbyWithHumans(3)
        b.sendMsg(ClientMessage.Leave)
        a.lobbyWithHumans(2)
        c.lobbyWithHumans(2)

        a.sendMsg(ClientMessage.StartHand)
        val ua = a.skipTo<ServerMessage.Update>().view
        val uc = c.skipTo<ServerMessage.Update>().view
        assertEquals(listOf(0, 2), ua.seats.map { it.seat })
        assertEquals(2, uc.mySeat)
        assertEquals(2, uc.myHole.size)
        assertTrue(ua.dealer in listOf(0, 2))
        assertTrue(ua.acting in listOf(0, 2))

        var va = ua
        var vc = uc
        var guard = 0
        while (va.phase.isBetting() && guard++ < 100) {
            when (va.acting) {
                0 -> a.sendMsg(ClientMessage.Act(if (va.legal.canCheck) PlayerAction.Check else PlayerAction.Call))
                2 -> c.sendMsg(ClientMessage.Act(if (vc.legal.canCheck) PlayerAction.Check else PlayerAction.Call))
            }
            va = a.skipTo<ServerMessage.Update>().view
            vc = c.skipTo<ServerMessage.Update>().view
        }
        assertEquals(Phase.HAND_OVER, va.phase)
        assertTrue(va.result!!.awards.single().winners.all { it in listOf(0, 2) })
        assertEquals(listOf(0, 2), vc.revealedHoles.keys.sorted())
        assertEquals(listOf(0, 2), vc.result!!.revealed.keys.sorted())
        a.close()
        c.close()
    }
}