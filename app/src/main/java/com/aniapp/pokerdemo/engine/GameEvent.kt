package com.aniapp.pokerdemo.engine

sealed interface GameEvent {
    data class BlindsPosted(val seat: Seat, val amount: Int) : GameEvent
    data object HoleCardsDealt : GameEvent
    data class ActionTaken(val seat: Seat, val action: PlayerAction) : GameEvent
    data class StreetDealt(val phase: Phase, val newCards: List<Card>, val isRunout: Boolean) : GameEvent
    data class UncalledReturned(val seat: Seat, val amount: Int) : GameEvent
    data object ShowdownRevealed : GameEvent
    data class PotAwarded(val winner: Seat?, val amount: Int) : GameEvent
    data class HandFinished(val result: HandResult) : GameEvent
    data class MatchFinished(val winner: Seat) : GameEvent
}