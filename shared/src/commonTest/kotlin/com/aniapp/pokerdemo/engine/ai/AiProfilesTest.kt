package com.aniapp.pokerdemo.engine.ai

import com.aniapp.pokerdemo.engine.ai.mc.MonteCarloAi
import com.aniapp.pokerdemo.engine.ai.mc.MonteCarloConfig
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AiProfilesTest {

    @Test
    fun casualMapsToRuleBasedOnBothSides() {
        assertIs<RuleBasedAi>(AiProfiles.local(AiDifficulty.CASUAL))
        assertIs<RuleBasedAi>(AiProfiles.server(AiDifficulty.CASUAL))
    }

    @Test
    fun standardAndAggressiveMapToMonteCarlo() {
        assertIs<MonteCarloAi>(AiProfiles.local(AiDifficulty.STANDARD))
        assertIs<MonteCarloAi>(AiProfiles.local(AiDifficulty.AGGRESSIVE))
        assertIs<MonteCarloAi>(AiProfiles.server(AiDifficulty.STANDARD))
        assertIs<MonteCarloAi>(AiProfiles.server(AiDifficulty.AGGRESSIVE))
    }

    @Test
    fun aggressiveRaisesBluffAndAggressionWithoutExtraCompute() {
        val balanced = MonteCarloConfig.Balanced
        val aggressive = MonteCarloConfig.Aggressive
        assertTrue(aggressive.aggression > balanced.aggression, "攻击性应上调")
        assertTrue(aggressive.bluffFrequency > balanced.bluffFrequency, "诈唬频率应上调")
        assertTrue(aggressive.valueThreshold < balanced.valueThreshold, "价值线应下探")
        assertTrue(aggressive.simulations == balanced.simulations, "模拟量不应变化")
        assertTrue(aggressive.timeBudgetNanos == balanced.timeBudgetNanos, "思考预算不应变化")

        val server = MonteCarloConfig.Server
        val serverAggressive = MonteCarloConfig.ServerAggressive
        assertTrue(serverAggressive.aggression > server.aggression)
        assertTrue(serverAggressive.bluffFrequency > server.bluffFrequency)
        assertTrue(serverAggressive.simulations == server.simulations)
        assertTrue(serverAggressive.timeBudgetNanos == server.timeBudgetNanos)
    }

    @Test
    fun defaultDifficultyIsStandard() {
        assertTrue(
            com.aniapp.pokerdemo.net.ClientMessage.CreateRoom("n").difficulty == AiDifficulty.STANDARD,
            "旧客户端缺省难度应回落到标准档"
        )
    }
}
