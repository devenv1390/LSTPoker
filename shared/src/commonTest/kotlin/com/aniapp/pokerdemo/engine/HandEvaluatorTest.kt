package com.aniapp.pokerdemo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HandEvaluatorTest {

    private fun cards(s: String): List<Card> = s.split(" ").map { tok ->
        val rank = when (val c = tok[0]) {
            'A' -> 14; 'K' -> 13; 'Q' -> 12; 'J' -> 11; 'T' -> 10
            else -> c.digitToInt()
        }
        val suit = when (tok[1]) {
            's' -> Suit.SPADES; 'h' -> Suit.HEARTS; 'd' -> Suit.DIAMONDS; else -> Suit.CLUBS
        }
        Card(rank, suit)
    }

    private fun handOf(s: String) = HandEvaluator.evaluate(cards(s))

    // 独立的 5 张笨评估器（oracle），实现方式与主评估器完全不同
    private fun oracle5(cards: List<Card>): Pair<Int, List<Int>> {
        require(cards.size == 5)
        val ranks = cards.map { it.rank }.sortedDescending()
        val flush = cards.map { it.suit }.distinct().size == 1
        val distinct = ranks.distinct()
        var straightHigh = 0
        if (distinct.size == 5) {
            if (distinct[0] - distinct[4] == 4) straightHigh = distinct[0]
            else if (distinct == listOf(14, 5, 4, 3, 2)) straightHigh = 5
        }
        val counts = ranks.groupingBy { it }.eachCount()
        val byCount = counts.entries.sortedWith(
            compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key }
        )
        return when {
            flush && straightHigh > 0 -> 8 to listOf(straightHigh)
            byCount[0].value == 4 -> 7 to listOf(byCount[0].key, byCount[1].key)
            byCount[0].value == 3 && byCount[1].value == 2 -> 6 to listOf(byCount[0].key, byCount[1].key)
            flush -> 5 to ranks
            straightHigh > 0 -> 4 to listOf(straightHigh)
            byCount[0].value == 3 -> 3 to listOf(byCount[0].key) + byCount.drop(1).map { it.key }
            byCount[0].value == 2 && byCount[1].value == 2 -> 2 to listOf(byCount[0].key, byCount[1].key, byCount[2].key)
            byCount[0].value == 2 -> 1 to listOf(byCount[0].key) + byCount.drop(1).map { it.key }
            else -> 0 to ranks
        }
    }

    private fun <T> combinations(list: List<T>, k: Int): List<List<T>> {
        if (k == 0) return listOf(emptyList())
        if (list.size < k) return emptyList()
        return combinations(list.drop(1), k - 1).map { listOf(list[0]) + it } +
            combinations(list.drop(1), k)
    }

    private fun oracleBest(cards: List<Card>): Pair<Int, List<Int>> =
        combinations(cards, 5).map { oracle5(it) }.maxWith(
            compareBy<Pair<Int, List<Int>>> { it.first }.thenBy { it.second.joinToString(",") { v -> v.toString().padStart(2, '0') } }
        )

    private fun assertMatchesOracle(cards: List<Card>) {
        val actual = HandEvaluator.evaluate(cards)
        val expected = oracleBest(cards)
        assertEquals("category mismatch for $cards", expected.first, actual.category.ordinal)
        assertEquals("tiebreaks mismatch for $cards", expected.second, actual.tiebreaks)
    }

    @Test
    fun flushKickerOnlyCountsFlushSuit() {
        // 6 张红心 + 杂色 A：旧实现会把杂色 A 混进同花比较
        val hand = handOf("Ah 9h 7h 5h 3h 2h As")
        assertEquals(HandCategory.FLUSH, hand.category)
        assertEquals(listOf(14, 9, 7, 5, 3), hand.tiebreaks)
        // 共享 4 张红心公共牌，第 5 张红心大者胜
        val board = cards("Ah Kh Qh Jh 2c")
        val p = HandEvaluator.evaluate(board + cards("9h 3d"))
        val a = HandEvaluator.evaluate(board + cards("8h 4d"))
        assertTrue(p > a)
    }

    @Test
    fun quadsKicker() {
        val p = handOf("9s 9h 9d 9c Ks 3h 2d")
        val a = handOf("9s 9h 9d 9c Qs Jh Td")
        assertEquals(HandCategory.QUADS, p.category)
        assertEquals(listOf(9, 13), p.tiebreaks)
        assertTrue(p > a)
    }

    @Test
    fun tripsKicker() {
        val p = handOf("8s 8h 8d Ac Ks 5h 2d")
        val a = handOf("8s 8h 8d Ac Qs Jh Td")
        assertEquals(HandCategory.TRIPS, p.category)
        assertEquals(listOf(8, 14, 13), p.tiebreaks)
        assertTrue(p > a)
    }

    @Test
    fun twoPairKickerAndThreePairs() {
        val p = handOf("As Ah Ks Kh 5d 3c 2h")
        val a = handOf("As Ah Ks Kh 4d 3c 2h")
        assertTrue(p > a)
        val threePairs = handOf("As Ah Ks Kh Qd Qc 7h")
        assertEquals(HandCategory.TWO_PAIR, threePairs.category)
        assertEquals(listOf(14, 13, 12), threePairs.tiebreaks)
    }

    @Test
    fun pairKicker() {
        val hand = handOf("Ks Kh Ac Qd Js 9c 3h")
        assertEquals(HandCategory.PAIR, hand.category)
        assertEquals(listOf(13, 14, 12, 11), hand.tiebreaks)
    }

    @Test
    fun fullHouseTwoTripsPicksBest() {
        assertEquals(listOf(14, 13), handOf("As Ah Ad Ks Kh Kd 2c").tiebreaks)
        assertEquals(listOf(14, 13), handOf("As Ah Ad Ks Kh Qd Qc").tiebreaks)
        // 双三条：次三条(2)拆对，单张 K 不成对
        assertEquals(listOf(14, 2), handOf("As Ah Ad 2s 2h 2d Kc").tiebreaks)
        assertEquals(listOf(8, 5), handOf("8s 8h 8d 5s 5h 3d 3c").tiebreaks)
    }

    @Test
    fun wheelStraight() {
        val wheel = handOf("As 2h 3d 4c 5s 9h Kd")
        assertEquals(HandCategory.STRAIGHT, wheel.category)
        assertEquals(listOf(5), wheel.tiebreaks)
        val sixHigh = handOf("2s 3h 4d 5c 6s 9h Kd")
        assertTrue(sixHigh > wheel)
        assertEquals(listOf(6), handOf("As 2h 3d 4c 5s 6h Kd").tiebreaks)
        assertEquals(listOf(14), handOf("As Kh Qd Jc Ts 2h 3d").tiebreaks)
    }

    @Test
    fun steelWheel() {
        val steel = handOf("Ah 2h 3h 4h 5h Ks Qd")
        assertEquals(HandCategory.STRAIGHT_FLUSH, steel.category)
        assertEquals(listOf(5), steel.tiebreaks)
        val sixHighSf = handOf("2h 3h 4h 5h 6h Ks Qd")
        assertTrue(sixHighSf > steel)
        val quads = handOf("9s 9h 9d 9c As Kh Qd")
        assertTrue(steel > quads)
    }

    @Test
    fun fiveAndSixCardInputs() {
        assertMatchesOracle(cards("As Kh Qd Jc Ts"))
        assertMatchesOracle(cards("Ah 2h 3h 4h 5h"))
        assertMatchesOracle(cards("As Ah Ad Ks Kh Kd"))
        assertMatchesOracle(cards("As Ah Ks Kh Qd Qc"))
    }

    @Test
    fun categoryHierarchy() {
        val chain = listOf(
            "As Kh 9d 7c 5s 3h 2d",
            "As Ah 9d 7c 5s 3h 2d",
            "As Ah Kd Kc 5s 3h 2d",
            "As Ah Ad 7c 5s 3h 2d",
            "5s 6h 7d 8c 9s Ah Kd",
            "Ah 9h 7h 5h 3h Ks Qd",
            "As Ah Ad Ks Kh 3h 2d",
            "As Ah Ad Ac Ks 3h 2d",
            "Ah Kh Qh Jh Th 3s 2d"
        ).map { handOf(it) }
        for (i in 0 until chain.size - 1) {
            assertTrue("level $i should lose to ${i + 1}", chain[i] < chain[i + 1])
        }
    }

    @Test
    fun flushBeatsStraightAndTrips() {
        val flushAndStraight = handOf("Ah Kh Qh Jh 9h Ts 9d")
        assertEquals(HandCategory.FLUSH, flushAndStraight.category)
        val flushAndTrips = handOf("Ah Kh Qh Jh 9h Ks Kd")
        assertEquals(HandCategory.FLUSH, flushAndTrips.category)
    }

    @Test
    fun boardPlaysTie() {
        val board = cards("As Ks Qs Js Ts")
        val p = HandEvaluator.evaluate(board + cards("2h 3h"))
        val a = HandEvaluator.evaluate(board + cards("4d 5d"))
        assertEquals(0, p.compareTo(a))
        val w1 = HandEvaluator.evaluate(cards("Ah 2h 3h 4h 5h 8s 9d"))
        val w2 = HandEvaluator.evaluate(cards("Ad 2d 3d 4d 5d 8c 9c"))
        assertEquals(0, w1.compareTo(w2))
    }

    @Test
    fun randomOracleCrossValidation() {
        val random = Random(42)
        repeat(1000) {
            val seven = createDeck().shuffled(random).take(7)
            assertMatchesOracle(seven)
        }
        repeat(300) {
            val six = createDeck().shuffled(random).take(6)
            assertMatchesOracle(six)
        }
    }

    @Test
    fun drawDetection() {
        val flushDraw = HandEvaluator.drawInfo(cards("Ah Kh"), cards("9h 7h 2c"))
        assertTrue(flushDraw.flushDraw)
        val openEnded = HandEvaluator.drawInfo(cards("5s 6h"), cards("7d 8c 2h"))
        assertTrue(openEnded.openEndedStraight)
        val gutshot = HandEvaluator.drawInfo(cards("5s 7h"), cards("8d 9c 2h"))
        assertTrue(gutshot.gutshot)
        assertTrue(!gutshot.openEndedStraight)
        val wheelDraw = HandEvaluator.drawInfo(cards("As 2h"), cards("3d 4c 9h"))
        assertTrue(wheelDraw.gutshot)
        assertTrue(!wheelDraw.openEndedStraight)
    }
}