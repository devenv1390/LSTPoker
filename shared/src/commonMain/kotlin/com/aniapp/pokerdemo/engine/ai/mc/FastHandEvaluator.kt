package com.aniapp.pokerdemo.engine.ai.mc

import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.HandCategory

/**
 * 蒙特卡洛模拟专用位运算牌力评估器。
 * 输出 score 与 [HandEvaluator.evaluate] 的 Long 编码完全一致，两者可直接互比与交叉验证。
 * 牌压成 Int：(rank-2)*4 + suit.ordinal。
 */
internal object FastHandEvaluator {

    fun pack(card: Card): Int = (card.rank - 2) * 4 + card.suit.ordinal

    // bit i = rank(i+2)；值 = 顺子最大牌 rank，0 表示无顺子。
    // 逐字复刻 HandEvaluator.straightHigh 语义（A 兼作 rank 1 参与轮子顺）。
    private val straightTable: IntArray by lazy {
        IntArray(1 shl 13) { mask -> computeStraightHigh(mask) }
    }

    private fun computeStraightHigh(mask: Int): Int {
        val list = ArrayList<Int>(14)
        for (r in 14 downTo 2) if (mask and (1 shl (r - 2)) != 0) list.add(r)
        if (mask and (1 shl 12) != 0) list.add(1)
        var run = 1
        for (i in 0 until list.size - 1) {
            if (list[i] - 1 == list[i + 1]) {
                run++
                if (run >= 5) return list[i - 3]
            } else {
                run = 1
            }
        }
        return 0
    }

    private fun packScore(category: HandCategory, tiebreaks: IntArray): Long {
        var s = (category.ordinal + 1).toLong()
        for (i in 0 until 5) s = s * 15 + tiebreaks[i]
        return s
    }

    /** 取除 exclude 外最大的 n 张牌（按张数展开，对子记两张；不足补 0），与 HandEvaluator.kickers 对齐 */
    private fun topCards(counts: IntArray, exclude: IntArray, n: Int, out: IntArray, outOff: Int) {
        var got = 0
        for (i in 12 downTo 0) {
            val r = i + 2
            if (exclude.any { it == r }) continue
            var c = counts[i]
            while (c > 0 && got < n) {
                out[outOff + got] = r
                got++
                c--
            }
            if (got == n) break
        }
        while (got < n) out[outOff + got++] = 0
    }

    private fun topDistinct(mask: Int, n: Int, out: IntArray, outOff: Int) {
        var got = 0
        for (i in 12 downTo 0) {
            if (mask and (1 shl i) != 0) {
                out[outOff + got] = i + 2
                got++
                if (got == n) break
            }
        }
        while (got < n) out[outOff + got++] = 0
    }

    fun evaluate(cards: IntArray, size: Int): Long {
        val suitMasks = IntArray(4)
        val counts = IntArray(13)
        var rankMask = 0
        for (i in 0 until size) {
            val r = cards[i] / 4 + 2
            val s = cards[i] % 4
            val bit = 1 shl (r - 2)
            suitMasks[s] = suitMasks[s] or bit
            counts[r - 2]++
            rankMask = rankMask or bit
        }

        var flushSuit = -1
        for (t in 0 until 4) if (countsOf(suitMasks[t]) >= 5) flushSuit = t

        val tb = IntArray(5)
        val tmp = IntArray(3)

        if (flushSuit >= 0) {
            val sf = straightTable[suitMasks[flushSuit]]
            if (sf > 0) {
                tb[0] = sf
                return packScore(HandCategory.STRAIGHT_FLUSH, tb)
            }
        }

        var quad = 0
        for (i in 12 downTo 0) if (counts[i] == 4) {
            quad = i + 2
            break
        }
        if (quad != 0) {
            topDistinct(rankMask and (1 shl (quad - 2)).inv(), 1, tmp, 0)
            tb[0] = quad
            tb[1] = tmp[0]
            return packScore(HandCategory.QUADS, tb)
        }

        var trips = 0
        for (i in 12 downTo 0) if (counts[i] >= 3) {
            trips = i + 2
            break
        }
        var pairRank = 0
        for (i in 12 downTo 0) if (counts[i] >= 2 && i + 2 != trips) {
            pairRank = i + 2
            break
        }
        if (trips != 0 && pairRank != 0) {
            tb[0] = trips
            tb[1] = pairRank
            return packScore(HandCategory.FULL_HOUSE, tb)
        }

        if (flushSuit >= 0) {
            topDistinct(suitMasks[flushSuit], 5, tb, 0)
            return packScore(HandCategory.FLUSH, tb)
        }

        val straight = straightTable[rankMask]
        if (straight > 0) {
            tb[0] = straight
            return packScore(HandCategory.STRAIGHT, tb)
        }

        if (trips != 0) {
            tb[0] = trips
            topCards(counts, intArrayOf(trips), 2, tmp, 0)
            tb[1] = tmp[0]
            tb[2] = tmp[1]
            return packScore(HandCategory.TRIPS, tb)
        }

        var hi = 0
        var lo = 0
        var pairs = 0
        for (i in 12 downTo 0) if (counts[i] >= 2) {
            pairs++
            if (pairs == 1) hi = i + 2 else if (pairs == 2) lo = i + 2
        }
        if (pairs >= 2) {
            tb[0] = hi
            tb[1] = lo
            topCards(counts, intArrayOf(hi, lo), 1, tmp, 0)
            tb[2] = tmp[0]
            return packScore(HandCategory.TWO_PAIR, tb)
        }
        if (pairs == 1) {
            tb[0] = hi
            topCards(counts, intArrayOf(hi), 3, tmp, 0)
            tb[1] = tmp[0]
            tb[2] = tmp[1]
            tb[3] = tmp[2]
            return packScore(HandCategory.PAIR, tb)
        }

        topDistinct(rankMask, 5, tb, 0)
        return packScore(HandCategory.HIGH_CARD, tb)
    }

    private fun countsOf(mask: Int): Int {
        var m = mask
        var c = 0
        while (m != 0) {
            m = m and (m - 1)
            c++
        }
        return c
    }
}
