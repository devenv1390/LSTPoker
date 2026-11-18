package com.aniapp.pokerdemo.session

import com.aniapp.pokerdemo.engine.GameEvent
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.net.SeatInfo
import com.aniapp.pokerdemo.net.TableView
import kotlinx.coroutines.flow.StateFlow

data class GameUiState(
    val table: TableView,
    val aiThinking: Boolean = false,
    val lastEvents: List<GameEvent> = emptyList(),
    val smallBlind: Int = 10,
    val bigBlind: Int = 20,
    val online: Boolean = false,
    val isHost: Boolean = true,
    val roomCode: String? = null
) {
    val isPlayerTurn: Boolean get() = table.acting == table.mySeat && table.phase.isBetting()
    val me: SeatInfo get() = table.seats.first { it.seat == table.mySeat }

    fun seatName(seat: Int): String =
        if (seat == table.mySeat) "你" else table.seats.firstOrNull { it.seat == seat }?.name ?: "玩家$seat"
}

interface GameSession {
    val uiState: StateFlow<GameUiState>
    fun act(action: PlayerAction)
    fun nextHand()
    fun newMatch()
    fun dispose()
}

fun emptyTableView(): TableView = TableView(
    seats = emptyList(),
    board = emptyList(),
    dealer = 0,
    acting = null,
    phase = Phase.IDLE,
    handNumber = 0,
    currentBet = 0,
    minRaiseTo = 0,
    pot = 0,
    mySeat = 0,
    myHole = emptyList(),
    legal = com.aniapp.pokerdemo.engine.LegalActions()
)