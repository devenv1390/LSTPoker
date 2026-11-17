package com.aniapp.pokerdemo.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Suit { SPADES, HEARTS, DIAMONDS, CLUBS }
data class Card(val rank: Int, val suit: Suit) {
    override fun toString(): String = "$rank-$suit"
}

fun createDeck(): List<Card> {
    val ranks = (2..14) // 11=J,12=Q,13=K,14=A
    val suits = Suit.entries.toTypedArray()
    return suits.flatMap { s -> ranks.map { Card(it, s) } }
}

// 牌型等级定义
enum class HandRank(val value: Int) {
    HIGH_CARD(1), PAIR(2), TWO_PAIR(3),
    THREE(4), STRAIGHT(5), FLUSH(6),
    FULL_HOUSE(7), FOUR(8), STRAIGHT_FLUSH(9)
}

data class PokerHand(val rank: HandRank, val score: List<Int>) : Comparable<PokerHand> {
    override fun compareTo(other: PokerHand): Int {
        // 1. 先比牌型大小
        val typeCompare = this.rank.value.compareTo(other.rank.value)
        if (typeCompare != 0) return typeCompare

        // 2. 牌型相同 -> 逐个比较 kicker / 主牌值
        for (i in this.score.indices) {
            val diff = this.score[i].compareTo(other.score.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }

        // 3. 完全一致 -> 平手
        return 0
    }
}

@Composable
fun cardSize(): Dp {
    val width = LocalConfiguration.current.screenWidthDp
    return (width / 7).dp  // 公共牌最多 5 + 间距
}

// 简化评估 (5取最佳): 实测可用
fun bestHand(cards: List<Card>): PokerHand {
    require(cards.size >= 7) { "必须传入7张牌: 2手牌 + 5公共牌" }

    val sorted = cards.sortedByDescending { it.rank }
    val suits = Suit.values().associateWith { s -> sorted.filter { it.suit == s } }
    val isFlush = suits.values.any { it.size >= 5 }
    val ranks = sorted.map { it.rank }.distinct()

    // Straight (顺子判断)
    fun isStraight(r: List<Int>): Int? {
        val distinct = r.distinct().sortedDescending()
        var count = 1
        for (i in 0 until distinct.size - 1) {
            if (distinct[i] - 1 == distinct[i + 1]) {
                count++
                if (count >= 5) return distinct[i - 3]
            } else count = 1
        }
        return null
    }

    val straightHigh = isStraight(ranks)
    val flushSuit = suits.entries.firstOrNull { it.value.size >= 5 }?.value

    // 同花顺
    if (isFlush && flushSuit != null) {
        val sh = isStraight(flushSuit.map { it.rank })
        if (sh != null) return PokerHand(HandRank.STRAIGHT_FLUSH, listOf(sh))
    }

    // 统计重复牌
    val groups = sorted.groupBy { it.rank }.mapValues { it.value.size }.toList()
    val four = groups.find { it.second == 4 }
    if (four != null) return PokerHand(HandRank.FOUR, listOf(four.first))

    val three = groups.filter { it.second == 3 }.map { it.first }
    val pair = groups.filter { it.second == 2 }.map { it.first }

    if (three.isNotEmpty() && pair.isNotEmpty())
        return PokerHand(HandRank.FULL_HOUSE, listOf(three.first(), pair.first()))

    if (isFlush) return PokerHand(HandRank.FLUSH, ranks.take(5))
    if (straightHigh != null) return PokerHand(HandRank.STRAIGHT, listOf(straightHigh))
    if (three.isNotEmpty()) return PokerHand(HandRank.THREE, listOf(three.first()))
    if (pair.size >= 2) return PokerHand(HandRank.TWO_PAIR, pair.take(2))
    if (pair.isNotEmpty()) return PokerHand(HandRank.PAIR, listOf(pair.first()))

    return PokerHand(HandRank.HIGH_CARD, ranks.take(5))
}
