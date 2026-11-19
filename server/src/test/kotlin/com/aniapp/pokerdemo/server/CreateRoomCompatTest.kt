package com.aniapp.pokerdemo.server

import com.aniapp.pokerdemo.engine.ai.AiDifficulty
import com.aniapp.pokerdemo.net.ClientMessage
import com.aniapp.pokerdemo.net.PokerJson
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

/** 旧客户端 JSON 不含 difficulty 字段时必须回落 STANDARD；新字段往返序列化一致。 */
class CreateRoomCompatTest {

    @Test
    fun legacyJsonWithoutDifficultyDecodesAsStandard() {
        val legacy = """{"type":"create","nickname":"老客户端","seatCount":4,"aiFill":false}"""
        val msg = PokerJson.decodeFromString<ClientMessage>(legacy)
        assertEquals(ClientMessage.CreateRoom("老客户端", 4, false), msg)
        assertEquals(AiDifficulty.STANDARD, (msg as ClientMessage.CreateRoom).difficulty)
    }

    @Test
    fun aggressiveDifficultyRoundTrips() {
        val msg: ClientMessage = ClientMessage.CreateRoom("n", 6, true, AiDifficulty.AGGRESSIVE)
        val json = PokerJson.encodeToString(msg)
        val decoded = PokerJson.decodeFromString<ClientMessage>(json) as ClientMessage.CreateRoom
        assertEquals(AiDifficulty.AGGRESSIVE, decoded.difficulty)
    }

    @Test
    fun unknownFieldsFromFutureClientsAreIgnored() {
        val future = """{"type":"create","nickname":"x","difficulty":"CASUAL","extraField":42}"""
        val msg = PokerJson.decodeFromString<ClientMessage>(future) as ClientMessage.CreateRoom
        assertEquals(AiDifficulty.CASUAL, msg.difficulty)
    }
}
