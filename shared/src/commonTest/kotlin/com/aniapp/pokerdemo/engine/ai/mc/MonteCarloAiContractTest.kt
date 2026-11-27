package com.aniapp.pokerdemo.engine.ai.mc

import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.ai.AiView
import com.aniapp.pokerdemo.engine.ai.aiViewOf
import com.aniapp.pokerdemo.engine.ai.sanitize
import com.aniapp.pokerdemo.engine.huEngine
import com.aniapp.pokerdemo.engine.testCards
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonteCarloAiContractTest {

    private val ai = MonteCarloAi(MonteCarloConfig.Fast)

    private fun assertLegal(legal: LegalActions, action: PlayerAction) {
        when (action) {
            PlayerAction.Fold -> assertTrue(legal.canFold, "非法 Fold")
            PlayerAction.Check -> assertTrue(legal.canCheck, "非法 Check")
            PlayerAction.Call -> assertTrue(legal.canCall, "非法 Call")
            is PlayerAction.RaiseTo -> {
                assertTrue(legal.canRaise, "非法 Raise")
                assertTrue(action.amount in legal.minRaiseTo!!..legal.maxRaiseTo!!, "加注额越界: ${action.amount}")
            }
        }
    }

    private fun viewOf(
        hole: String,
        board: String,
        phase: Phase,
        pot: Int,
        toCall: Int,
        chips: Int
    ): AiView = AiView(
        hole = testCards(hole),
        board = testCards(board),
        phase = phase,
        pot = pot,
        toCall = toCall,
        streetBet = 0,
        chips = chips,
        bigBlind = 20
    )

    @Test
    fun alwaysLegalInRandomGameStates() {
        val random = Random(11)
        val engine = huEngine(random = random)
        engine.startMatch()
        var checked = 0
        var hands = 0
        while (checked < 200 && hands < 500) {
            engine.startHand()
            var guard = 0
            while (engine.state.phase.isBetting()) {
                check(++guard < 200)
                val seat = engine.state.acting!!
                val legal = engine.legalActions(seat)
                val view = aiViewOf(engine.state, seat, legal.callAmount, engine.config.bigBlind)
                val action = ai.decide(view, legal, random)
                assertLegal(legal, action)
                checked++
                engine.act(seat, action)
            }
            hands++
            if (engine.state.phase == Phase.MATCH_OVER) engine.startMatch()
        }
        assertTrue(checked >= 200, "样本不足: $checked")
    }

    @Test
    fun boardRoyalFlushNeverFolds() {
        // 面皇家同花顺：我方与所有对手平局，equity=0.5，仍优于底池赔率，不应弃牌
        val view = viewOf("2h 3h", "As Ks Qs Js Ts", Phase.RIVER, pot = 400, toCall = 200, chips = 1000)
        val legal = LegalActions(canFold = true, canCheck = false, callAmount = 200, minRaiseTo = 400, maxRaiseTo = 1000)
        val random = Random(5)
        repeat(200) {
            val action = ai.decide(view, legal, random)
            assertTrue(action !is PlayerAction.Fold, "坚果平局牌不应弃牌")
            assertLegal(legal, action)
        }
    }

    @Test
    fun quadsVsShoveNeverFolds() {
        val view = viewOf("7h 7c", "7s 7d 2h 5c 9s", Phase.RIVER, pot = 400, toCall = 200, chips = 1000)
        val legal = LegalActions(canFold = true, canCheck = false, callAmount = 200, minRaiseTo = 400, maxRaiseTo = 1000)
        repeat(200) {
            val action = ai.decide(view, legal, Random(it))
            assertTrue(action !is PlayerAction.Fold, "四条不应弃牌")
        }
    }

    @Test
    fun junkFoldsToShove() {
        val view = viewOf("2h 7c", "As Kd Qh", Phase.FLOP, pot = 1000, toCall = 1000, chips = 1000)
        val legal = LegalActions(canFold = true, canCheck = false, callAmount = 1000, minRaiseTo = null, maxRaiseTo = null)
        repeat(200) {
            val action = ai.decide(view, legal, Random(it))
            assertTrue(action is PlayerAction.Fold, "垃圾牌面对全下应弃牌")
        }
    }

    @Test
    fun decisionFromEquityExplainsPotOddsCall() {
        val view = viewOf("Ah Kh", "As 7d 2c", Phase.FLOP, pot = 300, toCall = 100, chips = 1000)
        val legal = LegalActions(canFold = true, callAmount = 100)
        val decision = MonteCarloAi(MonteCarloConfig.Assistance)
            .decideFromEquity(view, legal, rawEquity = 0.40, random = Random(1))

        assertEquals(PlayerAction.Call, decision.action)
        assertEquals(MonteCarloReason.POT_ODDS_CALL, decision.reason)
    }

    @Test
    fun decideMatchesExtractedDecisionForFixedRiverEquity() {
        val view = viewOf("7h 7c", "7s 7d 2h 5c 9s", Phase.RIVER, pot = 400, toCall = 0, chips = 1000)
        val legal = LegalActions(canFold = true, canCheck = true, minRaiseTo = 40, maxRaiseTo = 1000)
        val ai = MonteCarloAi(MonteCarloConfig.Assistance)
        val raw = EquitySimulator().equity(
            hole = view.hole,
            board = view.board,
            opponentCount = 1,
            random = Random(21),
            maxSimulations = 1200,
            timeBudgetNanos = 30_000_000
        ).equity

        val original = ai.decide(view, legal, Random(21))
        val extracted = ai.decideFromEquity(view, legal, raw, Random(21)).action

        assertEquals(original, extracted)
    }

    @Test
    fun sanitizeNeverBreaksLegality() {
        val legal = LegalActions(canFold = true, canCheck = false, callAmount = 50, minRaiseTo = null, maxRaiseTo = null)
        assertTrue(sanitize(PlayerAction.RaiseTo(9999), legal) is PlayerAction.Call)
        assertTrue(sanitize(PlayerAction.Check, legal) is PlayerAction.Call)
        val raiseLegal = LegalActions(canFold = true, canCheck = false, callAmount = 20, minRaiseTo = 40, maxRaiseTo = 500)
        assertTrue(sanitize(PlayerAction.RaiseTo(10), raiseLegal) == PlayerAction.RaiseTo(40))
        assertTrue(sanitize(PlayerAction.RaiseTo(9999), raiseLegal) == PlayerAction.RaiseTo(500))
    }
}
