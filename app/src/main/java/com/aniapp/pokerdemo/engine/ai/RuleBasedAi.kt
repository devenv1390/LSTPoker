package com.aniapp.pokerdemo.engine.ai

import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.HandCategory
import com.aniapp.pokerdemo.engine.HandEvaluator
import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.PlayerAction
import kotlin.math.roundToInt
import kotlin.random.Random

class RuleBasedAi : AiStrategy {

    override fun decide(view: AiView, legal: LegalActions, random: Random): PlayerAction {
        val strength = (handStrength(view) + random.nextDouble(-0.06, 0.06)).coerceIn(0.0, 1.0)
        val aggression = 0.8 + random.nextDouble() * 0.4
        val raiseThreshold = 0.7 / aggression

        fun raiseTarget(fraction: Double): Int {
            val min = legal.minRaiseTo!!
            val max = legal.maxRaiseTo!!
            val potAfterCall = view.pot + view.toCall
            var target = view.streetBet + view.toCall + (potAfterCall * fraction).roundToInt()
            target = (target / view.bigBlind) * view.bigBlind
            target = target.coerceIn(min, max)
            if (target - view.streetBet > view.chips * 0.7) target = max
            return target
        }

        if (view.toCall == 0) {
            if (strength >= raiseThreshold && legal.canRaise && random.nextDouble() < 0.8) {
                return PlayerAction.RaiseTo(raiseTarget(0.5 + random.nextDouble() * 0.5))
            }
            if (strength < 0.4 && legal.canRaise && random.nextDouble() < 0.10) {
                return PlayerAction.RaiseTo(raiseTarget(0.6))
            }
            return PlayerAction.Check
        }
        if (strength >= raiseThreshold && legal.canRaise && random.nextDouble() < 0.6) {
            return PlayerAction.RaiseTo(raiseTarget(0.6 + random.nextDouble() * 0.4))
        }
        val potOdds = view.toCall.toDouble() / (view.pot + view.toCall)
        if (strength >= potOdds + 0.05) return PlayerAction.Call
        return PlayerAction.Fold
    }

    private fun handStrength(view: AiView): Double {
        if (view.board.size < 3) return preflopStrength(view.hole)
        val value = HandEvaluator.evaluate(view.hole + view.board)
        var s = when (value.category) {
            HandCategory.HIGH_CARD -> 0.15
            HandCategory.PAIR -> if (value.tiebreaks[0] >= view.board.maxOf { it.rank }) 0.45 else 0.30
            HandCategory.TWO_PAIR -> 0.55
            HandCategory.TRIPS -> 0.65
            HandCategory.STRAIGHT -> 0.75
            HandCategory.FLUSH -> 0.80
            HandCategory.FULL_HOUSE -> 0.90
            HandCategory.QUADS, HandCategory.STRAIGHT_FLUSH -> 0.98
        }
        if (value.category.ordinal <= HandCategory.TRIPS.ordinal && view.board.size < 5) {
            val draw = HandEvaluator.drawInfo(view.hole, view.board)
            var bonus = 0.0
            if (draw.flushDraw) bonus += 0.15
            if (draw.openEndedStraight) bonus += 0.12 else if (draw.gutshot) bonus += 0.06
            if (view.board.size == 4) bonus *= 0.5
            s += bonus
        }
        return s.coerceIn(0.0, 1.0)
    }

    private fun preflopStrength(hole: List<Card>): Double {
        val sorted = hole.sortedByDescending { it.rank }
        val hi = sorted[0]
        val lo = sorted[1]
        if (hi.rank == lo.rank) return maxOf(0.5, hi.rank / 14.0 * 0.85)
        var points = when (hi.rank) {
            14 -> 10.0; 13 -> 8.0; 12 -> 7.0; 11 -> 6.0
            else -> hi.rank / 2.0
        }
        if (hi.suit == lo.suit) points += 2.0
        val gap = hi.rank - lo.rank - 1
        points -= when (gap) {
            0 -> 0.0; 1 -> 1.0; 2 -> 2.0; 3 -> 4.0; else -> 5.0
        }
        if (gap <= 1 && hi.rank < 12) points += 1.0
        return (points / 20.0).coerceIn(0.05, 0.95)
    }
}