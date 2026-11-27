package com.aniapp.pokerdemo.engine.ai.mc

import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.ai.AiStrategy
import com.aniapp.pokerdemo.engine.ai.AiView
import kotlin.math.roundToInt
import kotlin.random.Random

data class MonteCarloConfig(
    val simulations: Int = 2000,
    val timeBudgetNanos: Long = 80_000_000,
    val aggression: Double = 1.0,
    val bluffFrequency: Double = 0.12,
    val strengthNoise: Double = 0.04,
    val valueThreshold: Double = 0.62
) {
    companion object {
        val Balanced = MonteCarloConfig()
        val Sharp = MonteCarloConfig(simulations = 3000, valueThreshold = 0.60)
        val Server = MonteCarloConfig(simulations = 1200, timeBudgetNanos = 40_000_000)
        val Fast = MonteCarloConfig(simulations = 600, timeBudgetNanos = 5_000_000_000)
        val Assistance = Server.copy(strengthNoise = 0.0, timeBudgetNanos = 30_000_000)

        // 激进档：全线放大加注概率(aggression)、诈唬频率上调、价值线下探；模拟量不动以保住思考时间预算
        val Aggressive = Balanced.copy(aggression = 1.3, bluffFrequency = 0.22, valueThreshold = 0.58)
        val ServerAggressive = Server.copy(aggression = 1.3, bluffFrequency = 0.22, valueThreshold = 0.58)
    }
}

enum class MonteCarloReason {
    VALUE_RAISE,
    THIN_VALUE_RAISE,
    BLUFF_RAISE,
    POT_ODDS_CALL,
    CHEAP_CALL,
    CHECK,
    FOLD
}

data class MonteCarloDecision(val action: PlayerAction, val reason: MonteCarloReason)

/**
 * 蒙特卡洛胜率驱动的 AI：equity 来自 [EquitySimulator]，决策按价值/薄价值/诈唬分层。
 * 诈唬只打低 equity 段（无摊牌价值才有弃牌率收益），跟注线挂钩底池赔率。
 */
class MonteCarloAi(
    private val config: MonteCarloConfig = MonteCarloConfig.Balanced,
    private val simulator: EquitySimulator = EquitySimulator()
) : AiStrategy {

    override fun decide(view: AiView, legal: LegalActions, random: Random): PlayerAction {
        val raw = simulator.equity(
            hole = view.hole,
            board = view.board,
            opponentCount = view.activeOpponents,
            random = random,
            maxSimulations = config.simulations,
            timeBudgetNanos = config.timeBudgetNanos
        ).equity
        return decideFromEquity(view, legal, raw, random).action
    }

    /** 使用已计算的 equity 做纯策略决策；辅助功能复用此入口，避免重复模拟。 */
    fun decideFromEquity(view: AiView, legal: LegalActions, rawEquity: Double, random: Random): MonteCarloDecision {
        val eq = if (config.strengthNoise == 0.0) {
            rawEquity.coerceIn(0.0, 1.0)
        } else {
            (rawEquity + random.nextDouble(-config.strengthNoise, config.strengthNoise)).coerceIn(0.0, 1.0)
        }
        val multiway = 0.05 * (view.activeOpponents - 1)
        val toCall = view.toCall
        val potOdds = if (toCall > 0) toCall.toDouble() / (view.pot + toCall) else 0.0
        val stp = view.chips.toDouble() / (view.pot + toCall).coerceAtLeast(1)

        fun raiseTo(fraction: Double): PlayerAction {
            val min = legal.minRaiseTo!!
            val max = legal.maxRaiseTo!!
            val potAfterCall = view.pot + view.toCall
            var target = view.streetBet + view.toCall + (potAfterCall * fraction).roundToInt()
            target = (target / view.bigBlind) * view.bigBlind
            target = target.coerceIn(min, max)
            if (target - view.streetBet > view.chips * 0.7) target = max
            return PlayerAction.RaiseTo(target)
        }

        if (toCall == 0) {
            if (eq >= config.valueThreshold + multiway && legal.canRaise &&
                random.nextDouble() < 0.75 * config.aggression
            ) {
                return MonteCarloDecision(raiseTo(0.5 + random.nextDouble() * 0.2), MonteCarloReason.VALUE_RAISE)
            }
            if (eq >= 0.50 + multiway && legal.canRaise &&
                random.nextDouble() < 0.35 * config.aggression
            ) {
                return MonteCarloDecision(raiseTo(0.3 + random.nextDouble() * 0.15), MonteCarloReason.THIN_VALUE_RAISE)
            }
            if (eq <= 0.35 && legal.canRaise && stp > 3 &&
                random.nextDouble() < config.bluffFrequency * (1 + multiway)
            ) {
                return MonteCarloDecision(raiseTo(0.7 + random.nextDouble() * 0.2), MonteCarloReason.BLUFF_RAISE)
            }
            return MonteCarloDecision(PlayerAction.Check, MonteCarloReason.CHECK)
        }

        if (eq >= config.valueThreshold + 0.08 + multiway && legal.canRaise &&
            random.nextDouble() < 0.5 * config.aggression
        ) {
            val action = if (stp < 2) PlayerAction.RaiseTo(legal.maxRaiseTo!!) else raiseTo(0.6 + random.nextDouble() * 0.4)
            return MonteCarloDecision(action, MonteCarloReason.VALUE_RAISE)
        }
        if (eq >= potOdds + 0.03) return MonteCarloDecision(PlayerAction.Call, MonteCarloReason.POT_ODDS_CALL)
        if (eq >= potOdds - 0.02 && (toCall <= view.bigBlind || stp > 8)) {
            return MonteCarloDecision(PlayerAction.Call, MonteCarloReason.CHEAP_CALL)
        }
        return MonteCarloDecision(PlayerAction.Fold, MonteCarloReason.FOLD)
    }
}
