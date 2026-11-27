package com.aniapp.pokerdemo.analysis

import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.HandCategory
import com.aniapp.pokerdemo.engine.HandEvaluator
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.ai.AiView
import com.aniapp.pokerdemo.engine.ai.mc.EquityResult
import com.aniapp.pokerdemo.engine.ai.mc.EquitySimulator
import com.aniapp.pokerdemo.engine.ai.mc.MonteCarloAi
import com.aniapp.pokerdemo.engine.ai.mc.MonteCarloConfig
import com.aniapp.pokerdemo.engine.ai.mc.MonteCarloReason
import com.aniapp.pokerdemo.engine.ai.sanitize
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/** 在客户端执行的本地数值分析；不接触引擎私有状态或对手手牌。 */
open class AssistanceAnalyzer(
    private val simulator: EquitySimulator = EquitySimulator(),
    private val config: MonteCarloConfig = MonteCarloConfig.Assistance
) {
    open fun analyze(situation: AdviceSituation, ensureActive: () -> Unit = {}): AssistanceAnalysis =
        analyze(situation, calculateEquitySync(situation, ensureActive))

    open suspend fun calculateEquity(situation: AdviceSituation, ensureActive: () -> Unit = {}): EquityResult =
        calculateEquitySync(situation, ensureActive)

    private fun calculateEquitySync(situation: AdviceSituation, ensureActive: () -> Unit): EquityResult =
        simulator.equity(
            hole = situation.hole,
            board = situation.board,
            opponentCount = situation.opponents.size,
            random = Random(situation.equityKey.hashCode()),
            maxSimulations = config.simulations,
            timeBudgetNanos = config.timeBudgetNanos,
            ensureActive = ensureActive
        )

    fun analyze(situation: AdviceSituation, result: EquityResult): AssistanceAnalysis {
        val equity = readoutOf(result, situation)
        val recommendation = if (situation.isPlayerTurn) recommend(situation, result.equity) else null
        val raisePresets = raisePresets(situation)
        val actions = buildList {
            if (situation.legal.canFold) add(actionEstimate(situation, PlayerAction.Fold, equity.value))
            if (situation.legal.canCheck) add(actionEstimate(situation, PlayerAction.Check, equity.value))
            if (situation.legal.canCall) add(actionEstimate(situation, PlayerAction.Call, equity.value))
            raisePresets.forEach { add(actionEstimate(situation, PlayerAction.RaiseTo(it), equity.value, it)) }
            val recommended = recommendation?.action as? PlayerAction.RaiseTo
            if (recommended != null && recommended.amount !in raisePresets) {
                add(actionEstimate(situation, recommended, equity.value, recommended.amount))
            }
        }
        return AssistanceAnalysis(
            adviceKey = situation.adviceKey,
            hand = handReadout(situation),
            equity = equity,
            actions = actions,
            recommendation = recommendation
        )
    }

    private fun recommend(situation: AdviceSituation, rawEquity: Double): Recommendation? {
        val view = AiView(
            hole = situation.hole,
            board = situation.board,
            phase = situation.phase,
            pot = situation.pot,
            toCall = situation.legal.callAmount,
            streetBet = situation.streetBet,
            chips = situation.chips,
            bigBlind = situation.bigBlind,
            activeOpponents = situation.opponents.size
        )
        val decision = MonteCarloAi(config, simulator)
            .decideFromEquity(view, situation.legal, rawEquity, Random(situation.adviceKey.hashCode()))
        val action = sanitize(decision.action, situation.legal)
        if (!situation.legal.accepts(action)) return null
        return Recommendation(action, reasonText(decision.reason))
    }

    private fun reasonText(reason: MonteCarloReason): String = when (reason) {
        MonteCarloReason.VALUE_RAISE -> "当前胜率较高，适合价值加注"
        MonteCarloReason.THIN_VALUE_RAISE -> "胜率尚可，可尝试较小价值加注"
        MonteCarloReason.BLUFF_RAISE -> "胜率偏低，但筹码深度允许偶尔诈唬；注意对手可能跟注"
        MonteCarloReason.POT_ODDS_CALL -> "当前胜率高于所需底池赔率，跟注有正参考价值"
        MonteCarloReason.CHEAP_CALL -> "跟注成本较低，保留听牌和后续选择"
        MonteCarloReason.CHECK -> "无需投入即可继续看牌"
        MonteCarloReason.FOLD -> "当前胜率不足以覆盖跟注成本"
    }

    private fun readoutOf(result: EquityResult, situation: AdviceSituation): EquityReadout {
        val method = if (situation.board.size == 5 && situation.opponents.size == 1) {
            EquityMethod.EXACT_HEADS_UP_RIVER
        } else {
            EquityMethod.MONTE_CARLO
        }
        val margin = if (method == EquityMethod.MONTE_CARLO && result.simulations > 0) {
            1.96 * sqrt(result.equity * (1.0 - result.equity) / result.simulations)
        } else null
        return EquityReadout(result.equity.coerceIn(0.0, 1.0), result.simulations, method, margin)
    }

    private fun actionEstimate(
        situation: AdviceSituation,
        action: PlayerAction,
        equity: Double,
        raiseAmount: Int? = null
    ): ActionEstimate {
        val ev = referenceEv(situation, action, equity)
        val label = when (action) {
            PlayerAction.Fold -> "弃牌"
            PlayerAction.Check -> "过牌"
            PlayerAction.Call -> if (situation.legal.callAmount >= situation.chips) "跟注全下" else "跟注 ${situation.legal.callAmount}"
            is PlayerAction.RaiseTo -> if (action.amount == situation.legal.maxRaiseTo) "全下 ${action.amount}" else "加注到 ${action.amount}"
        }
        return ActionEstimate(action, label, ev, raiseAmount)
    }

    private fun raisePresets(situation: AdviceSituation): List<Int> {
        val legal = situation.legal
        val min = legal.minRaiseTo ?: return emptyList()
        val max = legal.maxRaiseTo ?: return emptyList()
        if (!legal.canRaise) return emptyList()
        val afterCallPot = situation.pot + legal.callAmount
        val base = situation.currentBet
        return listOf(
            min,
            roundedRaise(base + (afterCallPot * 0.5).roundToInt(), min, max, situation.bigBlind),
            roundedRaise(base + afterCallPot, min, max, situation.bigBlind),
            max
        ).distinct().sorted()
    }

    private fun roundedRaise(target: Int, min: Int, max: Int, bigBlind: Int): Int =
        ((target / bigBlind) * bigBlind).coerceIn(min, max)

    private fun handReadout(situation: AdviceSituation): HandReadout {
        if (situation.phase == Phase.PREFLOP) return HandReadout(preflopLabel(situation.hole))
        val value = HandEvaluator.evaluate(situation.hole + situation.board)
        val draws = if ((situation.phase == Phase.FLOP || situation.phase == Phase.TURN) &&
            value.category.ordinal < HandCategory.STRAIGHT.ordinal
        ) {
            val info = HandEvaluator.drawInfo(situation.hole, situation.board)
            buildList {
                if (info.flushDraw) add("同花听牌")
                if (info.openEndedStraight) add("两端顺子听牌")
                if (info.gutshot) add("内听顺子")
            }
        } else emptyList()
        return HandReadout(value.category.label, draws)
    }

    private fun preflopLabel(hole: List<Card>): String {
        val ordered = hole.sortedByDescending { it.rank }
        val ranks = ordered.map { rankLabel(it.rank) }
        if (ordered[0].rank == ordered[1].rank) return "口袋对 ${ranks[0]}"
        val suited = if (ordered[0].suit == ordered[1].suit) "·同花" else "·异色"
        return "${ranks[0]}-${ranks[1]}$suited"
    }

    private fun rankLabel(rank: Int): String = when (rank) {
        14 -> "A"
        13 -> "K"
        12 -> "Q"
        11 -> "J"
        10 -> "T"
        else -> rank.toString()
    }
}

internal fun referenceEv(situation: AdviceSituation, action: PlayerAction, equity: Double): Double = when (action) {
    PlayerAction.Fold -> 0.0
    PlayerAction.Check -> equity * situation.pot
    PlayerAction.Call -> {
        val call = situation.legal.callAmount
        equity * (situation.pot + call) - call
    }
    is PlayerAction.RaiseTo -> {
        val matched = situation.opponents.sumOf { opponent ->
            val target = minOf(action.amount, opponent.streetBet + opponent.chips)
            (target - opponent.streetBet).coerceAtLeast(0)
        }
        val paidContribution = (minOf(action.amount, situation.opponents.maxOf { it.streetBet + it.chips }) -
            situation.streetBet).coerceAtLeast(0)
        equity * (situation.pot + paidContribution + matched) - paidContribution
    }
}
