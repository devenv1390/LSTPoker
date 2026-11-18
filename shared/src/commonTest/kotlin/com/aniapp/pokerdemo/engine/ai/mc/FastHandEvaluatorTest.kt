package com.aniapp.pokerdemo.engine.ai.mc

import com.aniapp.pokerdemo.engine.HandEvaluator
import com.aniapp.pokerdemo.engine.createDeck
import com.aniapp.pokerdemo.engine.testCards
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class FastHandEvaluatorTest {

    private fun assertEqualScores(hand: String) {
        val cards = testCards(hand)
        val packed = IntArray(cards.size) { FastHandEvaluator.pack(cards[it]) }
        assertEquals(
            HandEvaluator.evaluate(cards).score,
            FastHandEvaluator.evaluate(packed, packed.size),
            "评估不一致: $cards"
        )
    }

    @Test
    fun matchesHandEvaluatorOnRandomHands() {
        val random = Random(42)
        val deck = createDeck()
        repeat(3000) {
            val size = 5 + random.nextInt(3)
            val cards = deck.shuffled(random).take(size)
            val packed = IntArray(size) { FastHandEvaluator.pack(cards[it]) }
            assertEquals(
                HandEvaluator.evaluate(cards).score,
                FastHandEvaluator.evaluate(packed, size),
                "评估不一致: $cards"
            )
        }
    }

    @Test
    fun matchesOnStructuredEdgeCases() {
        assertEqualScores("Ah Kh Qh Jh Th 2s 3d")   // 皇家同花顺 + 边牌
        assertEqualScores("5h 4h 2s 3s 4d 5d 6c")   // 2-6 顺子优先于两对
        assertEqualScores("Ac As Kd Kc Qh Jd Ts")   // A 高顺优先于三条两对
        assertEqualScores("9h 9d 9s 2c 2h Kd Qc")   // 葫芦 9 带 2
        assertEqualScores("Ah Ad As Kd Kc Qh Jd")   // 葫芦 A 带 K
        assertEqualScores("Th Td 9h 9d 8s 7s 2c")   // 两对 T9 带 8
        assertEqualScores("Ah 2d 3c 4s 5h Kd Qc")   // 轮子顺 A-5
        assertEqualScores("6s 7s 8s 9s Ts Jd 2c")   // 同花顺 6-high? 实为 T 高 SF 判定
        assertEqualScores("Kh Qh Jh Th 9h 8s 7s")   // 同花顺 K-high
        assertEqualScores("2h 2d 2c 2s Ac Kd Qh")   // 四条带 A kicker
        assertEqualScores("3h 3d 3c 2s 2h Kd Qc")   // 葫芦 3 带 2
        assertEqualScores("Ah As Kd Kc Qh Qd Jc")   // 三对取最高两对 + Q kicker
        assertEqualScores("Th 9h 8d 7c 6s 5d Ks")   // T 高顺
        assertEqualScores("5h 5d 5c 9h 9d 3s 2c")   // 三条 kicker 用对子 9 两次
        assertEqualScores("Ah Kd Qc Jh 9c 7s 3d")   // 高牌 A K Q J 9
        assertEqualScores("Jh Jd 8c 7s 5h 4d 2c")   // 一对 J 三 kicker
        assertEqualScores("9h 9c 2d 2h 5s 5d 3c")   // 两对 95 带 3? 应为两对 9-5 kicker 3
        assertEqualScores("Ah Ad 2c 2d 3s 4d 5c")   // 两对 A2? 轮子顺 3-4-5-A-2 优先
    }
}
