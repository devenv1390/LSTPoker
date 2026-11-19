package com.aniapp.pokerdemo.engine.ai

import com.aniapp.pokerdemo.engine.ai.mc.MonteCarloAi
import com.aniapp.pokerdemo.engine.ai.mc.MonteCarloConfig
import kotlinx.serialization.Serializable

@Serializable
enum class AiDifficulty(val label: String) {
    CASUAL("休闲"),
    STANDARD("标准"),
    AGGRESSIVE("激进")
}

object AiProfiles {

    fun local(difficulty: AiDifficulty): AiStrategy = when (difficulty) {
        AiDifficulty.CASUAL -> RuleBasedAi()
        AiDifficulty.STANDARD -> MonteCarloAi(MonteCarloConfig.Balanced)
        AiDifficulty.AGGRESSIVE -> MonteCarloAi(MonteCarloConfig.Aggressive)
    }

    fun server(difficulty: AiDifficulty): AiStrategy = when (difficulty) {
        AiDifficulty.CASUAL -> RuleBasedAi()
        AiDifficulty.STANDARD -> MonteCarloAi(MonteCarloConfig.Server)
        AiDifficulty.AGGRESSIVE -> MonteCarloAi(MonteCarloConfig.ServerAggressive)
    }
}
