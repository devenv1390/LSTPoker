package com.aniapp.pokerdemo.analysis

import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.net.TableView

/** 只包含客户端可见公共信息与己方手牌的决策局面。 */
data class AdviceSituation(
    val hole: List<Card>,
    val board: List<Card>,
    val phase: Phase,
    val handNumber: Int,
    val acting: Int?,
    val mySeat: Int,
    val pot: Int,
    val currentBet: Int,
    val streetBet: Int,
    val chips: Int,
    val bigBlind: Int,
    val legal: LegalActions,
    val opponents: List<PublicOpponent>
) {
    val isPlayerTurn: Boolean get() = acting == mySeat && phase.isBetting()

    val equityKey: String
        get() = "equity-v1|${cardsKey(hole)}|${cardsKey(board)}|${opponents.size}"

    val adviceKey: String
        get() = buildString {
            append(equityKey)
            append("|h").append(handNumber)
            append("|p").append(phase.name)
            append("|a").append(acting)
            append("|seat").append(mySeat)
            append("|pot").append(pot)
            append("|bet").append(currentBet)
            append("|street").append(streetBet)
            append("|chips").append(chips)
            append("|bb").append(bigBlind)
            append("|legal").append(legal.canFold).append(',').append(legal.canCheck)
                .append(',').append(legal.callAmount).append(',').append(legal.minRaiseTo)
                .append(',').append(legal.maxRaiseTo)
            opponents.forEach { o ->
                append("|o").append(o.seat).append(',').append(o.streetBet)
                    .append(',').append(o.totalCommitted).append(',').append(o.chips)
                    .append(',').append(o.allIn)
            }
        }

    companion object {
        fun from(table: TableView, bigBlind: Int): AdviceSituation? {
            if (!table.phase.isBetting() || bigBlind <= 0 || table.myHole.size != 2) return null
            if (table.board.size != boardSize(table.phase)) return null
            val known = HashSet<Card>()
            if (table.myHole.any { !known.add(it) } || table.board.any { !known.add(it) }) return null
            val me = table.seats.firstOrNull { it.seat == table.mySeat } ?: return null
            if (listOf(me.chips, me.streetBet, me.totalCommitted, table.pot, table.currentBet).any { it < 0 }) return null
            val opponents = table.seats
                .asSequence()
                .filter { it.seat != table.mySeat && !it.folded }
                .sortedBy { it.seat }
                .map { PublicOpponent(it.seat, it.streetBet, it.totalCommitted, it.chips, it.allIn) }
                .toList()
            if (opponents.isEmpty() || opponents.size > 5) return null
            if (opponents.any { it.chips < 0 || it.streetBet < 0 || it.totalCommitted < 0 }) return null
            if (table.acting == table.mySeat && !hasLegalAction(table.legal)) return null
            if (table.legal.minRaiseTo != null && table.legal.maxRaiseTo != null) {
                val min = table.legal.minRaiseTo
                val max = table.legal.maxRaiseTo
                if (min <= 0 || max < min || max != me.streetBet + me.chips) return null
            }
            return AdviceSituation(
                hole = table.myHole.toList(),
                board = table.board.toList(),
                phase = table.phase,
                handNumber = table.handNumber,
                acting = table.acting,
                mySeat = table.mySeat,
                pot = table.pot,
                currentBet = table.currentBet,
                streetBet = me.streetBet,
                chips = me.chips,
                bigBlind = bigBlind,
                legal = table.legal,
                opponents = opponents
            )
        }

        private fun boardSize(phase: Phase): Int = when (phase) {
            Phase.PREFLOP -> 0
            Phase.FLOP -> 3
            Phase.TURN -> 4
            Phase.RIVER -> 5
            else -> -1
        }

        private fun hasLegalAction(legal: LegalActions): Boolean =
            legal.canFold || legal.canCheck || legal.canCall || legal.canRaise

        private fun cardsKey(cards: List<Card>): String = cards.joinToString(",") { it.toString() }
    }
}

data class PublicOpponent(
    val seat: Int,
    val streetBet: Int,
    val totalCommitted: Int,
    val chips: Int,
    val allIn: Boolean
)

enum class EquityMethod { MONTE_CARLO, EXACT_HEADS_UP_RIVER }

data class EquityReadout(
    val value: Double,
    val simulations: Int,
    val method: EquityMethod,
    val margin95: Double?
)

data class HandReadout(val label: String, val draws: List<String> = emptyList())

data class ActionEstimate(
    val action: PlayerAction,
    val label: String,
    val referenceEv: Double,
    val raiseAmount: Int? = null
)

data class Recommendation(val action: PlayerAction, val reason: String)

data class AssistanceAnalysis(
    val adviceKey: String,
    val hand: HandReadout,
    val equity: EquityReadout,
    val actions: List<ActionEstimate>,
    val recommendation: Recommendation?
)

sealed interface AssistanceState {
    data object Off : AssistanceState
    data object Loading : AssistanceState
    data object Unavailable : AssistanceState
    data class Ready(val analysis: AssistanceAnalysis) : AssistanceState
}
