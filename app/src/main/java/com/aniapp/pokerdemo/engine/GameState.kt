package com.aniapp.pokerdemo.engine

enum class Seat {
    PLAYER, AI;

    fun other(): Seat = if (this == PLAYER) AI else PLAYER
}

enum class Phase {
    IDLE, PREFLOP, FLOP, TURN, RIVER, SHOWDOWN, HAND_OVER, MATCH_OVER;

    fun isBetting(): Boolean = this == PREFLOP || this == FLOP || this == TURN || this == RIVER
}

data class PlayerState(
    val chips: Int,
    val hole: List<Card> = emptyList(),
    val streetBet: Int = 0,
    val totalCommitted: Int = 0,
    val isFolded: Boolean = false,
    val isAllIn: Boolean = false,
    val hasActedThisRound: Boolean = false,
    val raiseReopened: Boolean = false
)

enum class EndReason { FOLD, SHOWDOWN }

data class HandResult(
    val winner: Seat?,
    val potAwarded: Int,
    val playerValue: HandValue?,
    val aiValue: HandValue?,
    val reason: EndReason
)

data class GameState(
    val handNumber: Int = 0,
    val dealer: Seat = Seat.PLAYER,
    val phase: Phase = Phase.IDLE,
    val board: List<Card> = emptyList(),
    val player: PlayerState,
    val ai: PlayerState,
    val acting: Seat? = null,
    val currentBet: Int = 0,
    val minRaiseTo: Int = 0,
    val result: HandResult? = null,
    val aiRevealed: Boolean = false
) {
    val pot: Int get() = player.totalCommitted + ai.totalCommitted

    fun of(seat: Seat): PlayerState = if (seat == Seat.PLAYER) player else ai
}