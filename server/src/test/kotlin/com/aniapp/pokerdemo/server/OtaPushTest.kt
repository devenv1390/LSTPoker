package com.aniapp.pokerdemo.server

import com.aniapp.pokerdemo.net.PokerJson
import com.aniapp.pokerdemo.net.ServerMessage
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class OtaPushTest {
    private fun request() = OtaPushRequest("campaign-1", 200, "1.0.200", "a".repeat(64))

    @Test
    fun `old error json remains compatible`() {
        val message = PokerJson.decodeFromString<ServerMessage>(
            """{"type":"error","code":"bad_message","message":"bad"}"""
        )
        assertEquals(ServerMessage.Error("bad_message", "bad"), message)
    }

    @Test
    fun `publishes only to ota connections and reports count`() = testApplication {
        application { module(1L..5L) }
        val wsClient = createClient { install(WebSockets) }
        val ota = wsClient.webSocketSession("/ws?role=ota&versionCode=100")
        val game = wsClient.webSocketSession("/ws")

        val response = client.post("/api/admin/v1/update-pushes") {
            header(HttpHeaders.Host, "127.0.0.1:8080")
            header("X-Admin-Request", "1")
            contentType(ContentType.Application.Json)
            setBody(PokerJson.encodeToString(request()))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val pushed = withTimeout(5_000) {
            val frame = ota.incoming.receive()
            assertIs<Frame.Text>(frame)
            PokerJson.decodeFromString<ServerMessage>(frame.readText())
        }
        val update = assertIs<ServerMessage.Error>(pushed)
        assertEquals("update_available", update.code)
        assertEquals("campaign-1", update.updateId)
        assertEquals(200, update.targetVersionCode)
        assertNull(withTimeoutOrNull(200) { game.incoming.receive() })

        ota.close()
        game.close()
    }

    @Test
    fun `publish endpoint rejects missing auth forwarded and lan host`() = testApplication {
        application { module(1L..5L) }
        suspend fun status(host: String = "127.0.0.1:8080", token: String? = "1", forwarded: String? = null) =
            client.post("/api/admin/v1/update-pushes") {
                header(HttpHeaders.Host, host)
                if (token != null) header("X-Admin-Request", token)
                if (forwarded != null) header("X-Forwarded-For", forwarded)
                contentType(ContentType.Application.Json)
                setBody(PokerJson.encodeToString(request()))
            }.status

        assertEquals(HttpStatusCode.Forbidden, status(token = null))
        assertEquals(HttpStatusCode.Forbidden, status(forwarded = "192.168.3.18"))
        assertEquals(HttpStatusCode.Forbidden, status(host = "192.168.3.18:8080"))
    }
}
