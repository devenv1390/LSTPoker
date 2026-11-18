package com.aniapp.pokerdemo.engine.ai.mc

import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.ai.RuleBasedAi
import com.aniapp.pokerdemo.engine.ai.aiViewOf
import com.aniapp.pokerdemo.engine.ai.sanitize
import com.aniapp.pokerdemo.engine.huEngine
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 对抗竞技场：蒙特卡洛 AI 与旧规则 AI 单挑，双方各坐一次座位消除位置偏差，
 * 断言 MC 方净胜筹码为正，作为"确实变强"的回归证据。
 */
class AiArenaTest {

    private fun playSide(seed: Int, mcSeat: Int, maxHands: Int): Int {
        val random = Random(seed)
        val engine = huEngine(random = random)
        val mc = MonteCarloAi(MonteCarloConfig.Fast)
        val rule = RuleBasedAi()
        engine.startMatch()
        var hands = 0
        while (hands < maxHands && engine.state.phase != Phase.MATCH_OVER) {
            engine.startHand()
            var guard = 0
            while (engine.state.phase.isBetting() && guard++ < 500) {
                val seat = engine.state.acting ?: break
                val legal = engine.legalActions(seat)
                val view = aiViewOf(engine.state, seat, legal.callAmount, engine.config.bigBlind)
                val action = sanitize(
                    if (seat == mcSeat) mc.decide(view, legal, random) else rule.decide(view, legal, random),
                    legal
                )
                engine.act(seat, action)
            }
            hands++
        }
        val players = engine.state.players
        return players[mcSeat].chips - players[1 - mcSeat].chips
    }

    @Test
    fun monteCarloOutperformsRuleBased() {
        val seat0Net = playSide(seed = 20261118, mcSeat = 0, maxHands = 120)
        val seat1Net = playSide(seed = 20261118, mcSeat = 1, maxHands = 120)
        val total = seat0Net + seat1Net
        println("AiArena: seat0Net=$seat0Net seat1Net=$seat1Net total=$total")
        assertTrue(total > 0, "蒙特卡洛 AI 未跑赢规则 AI: $seat0Net + $seat1Net = $total")
    }
}
