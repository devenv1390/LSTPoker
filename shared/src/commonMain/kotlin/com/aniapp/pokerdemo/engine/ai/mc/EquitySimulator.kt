package com.aniapp.pokerdemo.engine.ai.mc

import com.aniapp.pokerdemo.engine.Card
import kotlin.random.Random
import kotlin.time.TimeSource

data class EquityResult(val equity: Double, val simulations: Int)

/**
 * 全下等价胜率（all-in equity）计算器。
 * 河牌单挑走精确枚举；其余场景 Monte Carlo 随机补对手手牌与公共牌竞速。
 * 对手范围默认均匀随机两张牌。
 */
class EquitySimulator {

    fun equity(
        hole: List<Card>,
        board: List<Card>,
        opponentCount: Int,
        random: Random,
        maxSimulations: Int,
        timeBudgetNanos: Long
    ): EquityResult {
        require(hole.size == 2) { "手牌必须 2 张，实际 ${hole.size}" }
        val opponents = opponentCount.coerceIn(1, 5)
        val myHole = intArrayOf(FastHandEvaluator.pack(hole[0]), FastHandEvaluator.pack(hole[1]))

        val known = BooleanArray(52)
        known[myHole[0]] = true
        known[myHole[1]] = true
        for (card in hole) known[FastHandEvaluator.pack(card)] = true
        val boardPacked = IntArray(board.size)
        for (i in board.indices) {
            val packed = FastHandEvaluator.pack(board[i])
            require(!known[packed]) { "手牌与公共牌存在重复牌张" }
            known[packed] = true
            boardPacked[i] = packed
        }

        val pool = IntArray(52 - 2 - board.size)
        var p = 0
        for (card in 0 until 52) if (!known[card]) pool[p++] = card

        val buf = IntArray(7)
        buf[0] = myHole[0]
        buf[1] = myHole[1]
        for (i in boardPacked.indices) buf[2 + i] = boardPacked[i]

        return if (board.size == 5 && opponents == 1) {
            enumerateRiver(buf, pool, p)
        } else {
            monteCarlo(buf, myHole, pool, p, board.size, opponents, random, maxSimulations, timeBudgetNanos)
        }
    }

    /** 河牌已定，单挑枚举对手全部 C(n,2) 手牌组合，精确胜率 */
    private fun enumerateRiver(buf: IntArray, pool: IntArray, n: Int): EquityResult {
        val my0 = buf[0]
        val my1 = buf[1]
        val myScore = FastHandEvaluator.evaluate(buf, 7)
        var done = 0
        var score = 0.0
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                buf[0] = pool[i]
                buf[1] = pool[j]
                val opp = FastHandEvaluator.evaluate(buf, 7)
                score += if (myScore > opp) 1.0 else if (myScore == opp) 0.5 else 0.0
                done++
            }
        }
        buf[0] = my0
        buf[1] = my1
        return EquityResult(if (done == 0) 0.0 else score / done, done)
    }

    private fun monteCarlo(
        buf: IntArray,
        myHole: IntArray,
        pool: IntArray,
        n: Int,
        boardCount: Int,
        opponents: Int,
        random: Random,
        maxSimulations: Int,
        timeBudgetNanos: Long
    ): EquityResult {
        val start = TimeSource.Monotonic.markNow()
        val needCommunity = 5 - boardCount
        val need = needCommunity + 2 * opponents
        val sims = maxSimulations.coerceAtLeast(1)
        var done = 0
        var score = 0.0

        while (done < sims) {
            if (done > 0 && done % 256 == 0 && start.elapsedNow().inWholeNanoseconds > timeBudgetNanos) break
            // 部分 Fisher-Yates：pool[0 until need] 为本轮抽取牌，牌池下一轮自动复用
            for (k in 0 until need) {
                val j = random.nextInt(k, n)
                val t = pool[k]
                pool[k] = pool[j]
                pool[j] = t
            }
            for (i in 0 until needCommunity) buf[2 + boardCount + i] = pool[i]
            buf[0] = myHole[0]
            buf[1] = myHole[1]
            val myScore = FastHandEvaluator.evaluate(buf, 7)

            var bestOpp = -1L
            var ties = 0
            var offset = needCommunity
            for (o in 0 until opponents) {
                buf[0] = pool[offset]
                buf[1] = pool[offset + 1]
                offset += 2
                val opp = FastHandEvaluator.evaluate(buf, 7)
                if (opp > bestOpp) bestOpp = opp
                if (opp == myScore) ties++
            }
            score += when {
                bestOpp < myScore -> 1.0
                bestOpp == myScore -> 1.0 / (ties + 1)
                else -> 0.0
            }
            done++
        }
        return EquityResult(if (done == 0) 0.0 else score / done, done)
    }
}
