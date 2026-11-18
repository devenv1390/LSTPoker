package com.aniapp.pokerdemo.engine

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class Suit { SPADES, HEARTS, DIAMONDS, CLUBS }

@Serializable
data class Card(val rank: Int, val suit: Suit) {
    override fun toString(): String {
        val r = when (rank) {
            14 -> "A"; 13 -> "K"; 12 -> "Q"; 11 -> "J"; 10 -> "T"
            else -> rank.toString()
        }
        val s = when (suit) {
            Suit.SPADES -> "s"; Suit.HEARTS -> "h"; Suit.DIAMONDS -> "d"; Suit.CLUBS -> "c"
        }
        return "$r$s"
    }
}

fun createDeck(): List<Card> = Suit.entries.flatMap { s -> (2..14).map { Card(it, s) } }

class Deck(cards: List<Card>) {
    constructor(random: Random = Random.Default) : this(createDeck().shuffled(random))

    private val remaining = ArrayDeque(cards)

    fun draw(): Card = remaining.removeFirst()

    fun burn() {
        if (remaining.isNotEmpty()) remaining.removeFirst()
    }
}