package com.aniapp.pokerdemo.engine.ai.mc

import com.aniapp.pokerdemo.engine.testCards
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EquitySimulatorTest {

    private val sim = EquitySimulator()
    private val noBudget = 10_000_000_000L

    private fun eq(hole: String, board: String = "", opps: Int = 1, sims: Int = 4000): EquityResult =
        sim.equity(
            hole = testCards(hole),
            board = if (board.isEmpty()) emptyList() else testCards(board),
            opponentCount = opps,
            random = Random(7),
            maxSimulations = sims,
            timeBudgetNanos = noBudget
        )

    @Test
    fun pocketAcesFavoriteHeadsUp() {
        val r = eq("Ah As")
        assertTrue(r.equity in 0.80..0.90, "AA 单挑胜率异常: ${r.equity}")
    }

    @Test
    fun trashHandWellBelowCoinflip() {
        val r = eq("7h 2c")
        assertTrue(r.equity in 0.28..0.40, "72o 单挑胜率异常: ${r.equity}")
    }

    @Test
    fun acesDiminishMultiway() {
        val r = eq("Ah As", opps = 3)
        assertTrue(r.equity in 0.60..0.75, "AA 三人池胜率异常: ${r.equity}")
    }

    @Test
    fun riverQuadsIsExactOneViaEnumeration() {
        val r = eq("7h 7c", board = "7s 7d 2h 5c 9s")
        assertEquals(45 * 44 / 2, r.simulations)
        assertEquals(1.0, r.equity, 1e-9)
    }

    @Test
    fun junkOnOvercardFlopIsTiny() {
        // 72o 撞上 A-K-Q 高张面：只剩对子撞牌的低概率，胜率应显著低于 1/4
        val r = eq("7h 2c", board = "As Kh Qd")
        assertTrue(r.equity < 0.25, "垃圾牌胜率应很低: ${r.equity}")
    }

    @Test
    fun timeBudgetTruncatesSimulations() {
        val r = sim.equity(
            hole = testCards("Ah Kh"),
            board = emptyList(),
            opponentCount = 1,
            random = Random(3),
            maxSimulations = 200_000,
            timeBudgetNanos = 1
        )
        assertTrue(r.simulations < 200_000, "应按预算截断: ${r.simulations}")
        assertTrue(r.simulations >= 256, "至少完成一轮块: ${r.simulations}")
    }

    @Test
    fun flopEquityRoughlyMatchesKnownValues() {
        // 顶对顶踢脚 vs 随机单挑对手，河牌前应有明显优势
        val r = eq("Ah Kh", board = "Kd 7c 2s")
        assertTrue(r.equity in 0.80..0.95, "KK顶对TPTK 胜率异常: ${r.equity}")
    }
}
