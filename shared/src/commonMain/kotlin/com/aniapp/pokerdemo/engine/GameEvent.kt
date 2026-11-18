package com.aniapp.pokerdemo.engine

import kotlinx.serialization.Serializable

@Serializable
sealed interface GameEvent {
    @Serializable
    data class BlindsPosted(val seat: Int, val amount: Int) : GameEvent
    @Serializable
    data object HoleCardsDealt : GameEvent
    @Serializable
    data class ActionTaken(val seat: Int, val action: PlayerAction) : GameEvent
    @Serializable
    data class StreetDealt(val phase: Phase, val newCards: List<Card>, val isRunout: Boolean) : GameEvent
    @Serializable
    data class UncalledReturned(val seat: Int, val amount: Int) : GameEvent
    @Serializable
    data object ShowdownRevealed : GameEvent
    @Serializable
    data class PotAwarded(val potIndex: Int, val winners: List<Int>, val amount: Int) : GameEvent
    @Serializable
    data class PlayerEliminated(val seat: Int) : GameEvent
    @Serializable
    data class HandFinished(val result: HandResult) : GameEvent
    @Serializable
    data class MatchFinished(val winner: Int) : GameEvent
}