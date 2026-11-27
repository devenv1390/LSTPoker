package com.aniapp.pokerdemo.net

import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.GameEvent
import com.aniapp.pokerdemo.engine.HandResult
import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.ai.AiDifficulty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val PokerJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
sealed interface ClientMessage {
    @Serializable
    @SerialName("create")
    data class CreateRoom(
        val nickname: String,
        val seatCount: Int = 6,
        val aiFill: Boolean = true,
        val difficulty: AiDifficulty = AiDifficulty.STANDARD
    ) : ClientMessage

    @Serializable
    @SerialName("join")
    data class JoinRoom(val code: String, val nickname: String) : ClientMessage

    @Serializable
    @SerialName("start")
    data object StartHand : ClientMessage

    @Serializable
    @SerialName("act")
    data class Act(val action: PlayerAction) : ClientMessage

    @Serializable
    @SerialName("ai_fill")
    data class SetAiFill(val enabled: Boolean) : ClientMessage

    @Serializable
    @SerialName("leave")
    data object Leave : ClientMessage
}

@Serializable
data class SeatInfo(
    val seat: Int,
    val name: String,
    val chips: Int,
    val streetBet: Int,
    val totalCommitted: Int,
    val folded: Boolean,
    val allIn: Boolean,
    val connected: Boolean,
    val isAi: Boolean
)

@Serializable
data class TableView(
    val seats: List<SeatInfo>,
    val board: List<Card>,
    val dealer: Int,
    val acting: Int?,
    val phase: Phase,
    val handNumber: Int,
    val currentBet: Int,
    val minRaiseTo: Int,
    val pot: Int,
    val mySeat: Int,
    val myHole: List<Card>,
    val legal: LegalActions,
    val result: HandResult? = null,
    val revealedHoles: Map<Int, List<Card>> = emptyMap()
)

@Serializable
data class LobbySeat(val seat: Int, val name: String, val isAi: Boolean, val connected: Boolean)

@Serializable
sealed interface ServerMessage {
    @Serializable
    @SerialName("joined")
    data class Joined(val roomCode: String, val mySeat: Int, val seatCount: Int) : ServerMessage

    @Serializable
    @SerialName("lobby")
    data class Lobby(
        val roomCode: String,
        val seats: List<LobbySeat>,
        val hostSeat: Int,
        val seatCount: Int,
        val aiFill: Boolean = true
    ) : ServerMessage

    @Serializable
    @SerialName("update")
    data class Update(val view: TableView, val events: List<GameEvent>) : ServerMessage

    @Serializable
    @SerialName("error")
    data class Error(
        val code: String,
        val message: String,
        val updateId: String? = null,
        val targetVersionCode: Int? = null
    ) : ServerMessage

    @Serializable
    @SerialName("closed")
    data object RoomClosed : ServerMessage
}