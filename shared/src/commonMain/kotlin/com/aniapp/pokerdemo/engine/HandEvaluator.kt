package com.aniapp.pokerdemo.engine

import kotlinx.serialization.Serializable

@Serializable
enum class HandCategory(val label: String) {
    HIGH_CARD("高牌"),
    PAIR("一对"),
    TWO_PAIR("两对"),
    TRIPS("三条"),
    STRAIGHT("顺子"),
    FLUSH("同花"),
    FULL_HOUSE("葫芦"),
    QUADS("四条"),
    STRAIGHT_FLUSH("同花顺")
}

@Serializable
data class HandValue(
    val category: HandCategory,
    val tiebreaks: List<Int>,
    val bestFive: List<Card>,
    val score: Long
) : Comparable<HandValue> {
    override fun compareTo(other: HandValue): Int = score.compareTo(other.score)
}

data class DrawInfo(
    val flushDraw: Boolean,
    val openEndedStraight: Boolean,
    val gutshot: Boolean
)

object HandEvaluator {

    fun evaluate(cards: List<Card>): HandValue {
        require(cards.size in 5..7) { "需要 5-7 张牌，实际 ${cards.size} 张" }
        val byRank = cards.groupBy { it.rank }
        val groups = byRank.entries.sortedWith(
            compareByDescending<Map.Entry<Int, List<Card>>> { it.value.size }.thenByDescending { it.key }
        )
        val flushCards = cards.groupBy { it.suit }.values.firstOrNull { it.size >= 5 }
        val straightHigh = straightHigh(byRank.keys)
        val sfHigh = flushCards?.let { fc -> straightHigh(fc.map { it.rank }.toSet()) }

        fun pack(category: HandCategory, tiebreaks: List<Int>, five: List<Card>): HandValue {
            var score = (category.ordinal + 1).toLong()
            for (i in 0 until 5) score = score * 15 + tiebreaks.getOrElse(i) { 0 }
            return HandValue(category, tiebreaks, five, score)
        }

        fun ofRank(rank: Int, n: Int): List<Card> = byRank.getValue(rank).take(n)

        fun kickers(excluded: Set<Int>, n: Int): List<Card> =
            cards.filter { it.rank !in excluded }.sortedByDescending { it.rank }.take(n)

        fun straightFive(high: Int, pool: List<Card>): List<Card> {
            val ranks = if (high == 5) listOf(5, 4, 3, 2, 14) else (high downTo high - 4).toList()
            return ranks.map { r -> pool.first { it.rank == r } }
        }

        if (sfHigh != null) {
            return pack(HandCategory.STRAIGHT_FLUSH, listOf(sfHigh), straightFive(sfHigh, flushCards!!))
        }

        groups.firstOrNull { it.value.size == 4 }?.let { entry ->
            val quad = entry.key
            val kicker = kickers(setOf(quad), 1)
            return pack(HandCategory.QUADS, listOf(quad) + kicker.map { it.rank }, ofRank(quad, 4) + kicker)
        }

        val trips = groups.filter { it.value.size >= 3 }.map { it.key }
        if (trips.isNotEmpty()) {
            val t = trips.first()
            val pairRank = groups.filter { it.value.size >= 2 && it.key != t }.maxOfOrNull { it.key }
            if (pairRank != null) {
                return pack(HandCategory.FULL_HOUSE, listOf(t, pairRank), ofRank(t, 3) + ofRank(pairRank, 2))
            }
        }

        if (flushCards != null) {
            val five = flushCards.sortedByDescending { it.rank }.take(5)
            return pack(HandCategory.FLUSH, five.map { it.rank }, five)
        }
        if (straightHigh != null) {
            return pack(HandCategory.STRAIGHT, listOf(straightHigh), straightFive(straightHigh, cards))
        }

        if (trips.isNotEmpty()) {
            val t = trips.first()
            val ks = kickers(setOf(t), 2)
            return pack(HandCategory.TRIPS, listOf(t) + ks.map { it.rank }, ofRank(t, 3) + ks)
        }

        val pairs = groups.filter { it.value.size >= 2 }.map { it.key }
        if (pairs.size >= 2) {
            val hi = pairs[0]
            val lo = pairs[1]
            val k = kickers(setOf(hi, lo), 1)
            return pack(HandCategory.TWO_PAIR, listOf(hi, lo) + k.map { it.rank }, ofRank(hi, 2) + ofRank(lo, 2) + k)
        }
        if (pairs.isNotEmpty()) {
            val p = pairs.first()
            val ks = kickers(setOf(p), 3)
            return pack(HandCategory.PAIR, listOf(p) + ks.map { it.rank }, ofRank(p, 2) + ks)
        }

        val five = cards.sortedByDescending { it.rank }.distinctBy { it.rank }.take(5)
        return pack(HandCategory.HIGH_CARD, five.map { it.rank }, five)
    }

    fun straightHigh(ranks: Set<Int>): Int? {
        val rs = if (14 in ranks) ranks + 1 else ranks
        val sorted = rs.sortedDescending()
        var run = 1
        for (i in 0 until sorted.size - 1) {
            if (sorted[i] - 1 == sorted[i + 1]) {
                run++
                if (run >= 5) return sorted[i - 3]
            } else {
                run = 1
            }
        }
        return null
    }

    fun drawInfo(hole: List<Card>, board: List<Card>): DrawInfo {
        val all = hole + board
        val flushDraw = all.groupBy { it.suit }.any { it.value.size == 4 }
        val rs = all.map { it.rank }.toSet().let { if (14 in it) it + 1 else it }
        var openEnded = false
        var gutshot = false
        for (r in 1..11) {
            if ((r..r + 3).all { it in rs }) {
                if (r >= 2 && r + 4 <= 14) openEnded = true else gutshot = true
            }
        }
        for (high in 5..14) {
            val window = if (high == 5) setOf(14, 2, 3, 4, 5) else (high - 4..high).toSet()
            if (window.count { it in rs } == 4) gutshot = true
        }
        return DrawInfo(flushDraw, openEnded, gutshot && !openEnded)
    }
}