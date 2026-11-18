package com.aniapp.pokerdemo.engine

import kotlinx.serialization.Serializable

@Serializable
enum class Phase {
    IDLE, PREFLOP, FLOP, TURN, RIVER, SHOWDOWN, HAND_OVER, MATCH_OVER;

    fun isBetting(): Boolean = this == PREFLOP || this == FLOP || this == TURN || this == RIVER
}

@Serializable
data class PlayerState(
    val id: Int,
    val name: String,
    val chips: Int,
    val hole: List<Card> = emptyList(),
    val streetBet: Int = 0,
    val totalCommitted: Int = 0,
    val isFolded: Boolean = false,
    val isAllIn: Boolean = false,
    val hasActedThisRound: Boolean = false,
    val raiseReopened: Boolean = false
)

@Serializable
enum class EndReason { FOLD, SHOWDOWN }

@Serializable
data class PotAward(val amount: Int, val winners: List<Int>, val bestValue: HandValue?)

@Serializable
data class HandResult(
    val awards: List<PotAward>,
    val revealed: Map<Int, HandValue>,
    val reason: EndReason
) {
    val totalPot: Int get() = awards.sumOf { it.amount }
}

@Serializable
data class GameState(
    val players: List<PlayerState>,
    val handNumber: Int = 0,
    val dealer: Int = 0,
    val phase: Phase = Phase.IDLE,
    val board: List<Card> = emptyList(),
    val acting: Int? = null,
    val currentBet: Int = 0,
    val minRaiseTo: Int = 0,
    val result: HandResult? = null,
    val showdown: Boolean = false
) {
    val pot: Int get() = players.sumOf { it.totalCommitted }

    fun of(seat: Int): PlayerState = players[seat]
}