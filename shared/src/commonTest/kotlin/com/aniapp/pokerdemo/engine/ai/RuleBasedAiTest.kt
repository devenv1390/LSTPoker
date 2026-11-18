package com.aniapp.pokerdemo.engine.ai

import com.aniapp.pokerdemo.engine.GameState
import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.huEngine
import com.aniapp.pokerdemo.engine.testCards
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class RuleBasedAiTest {

    private val ai = RuleBasedAi()

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

    private fun viewOf(state: GameState, seat: Int, legal: LegalActions): AiView {
        val me = state.of(seat)
        return AiView(
            me.hole, state.board, state.phase, state.pot, legal.callAmount,
            me.streetBet, me.chips, 20,
            activeOpponents = state.players.count { !it.isFolded && it.id != seat }
        )
    }

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
                val action = ai.decide(viewOf(engine.state, seat, legal), legal, random)
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
    fun nutsNeverFolds() {
        val view = AiView(
            hole = testCards("2h 3h"),
            board = testCards("As Ks Qs Js Ts"),
            phase = Phase.RIVER,
            pot = 400, toCall = 200, streetBet = 0, chips = 1000, bigBlind = 20
        )
        val legal = LegalActions(canFold = true, canCheck = false, callAmount = 200, minRaiseTo = 400, maxRaiseTo = 1000)
        val random = Random(5)
        repeat(200) {
            val action = ai.decide(view, legal, random)
            assertTrue(action !is PlayerAction.Fold, "坚果牌不应弃牌")
            assertLegal(legal, action)
        }
    }

    @Test
    fun junkFoldsToShove() {
        val view = AiView(
            hole = testCards("2h 7c"),
            board = testCards("As Kd Qh"),
            phase = Phase.FLOP,
            pot = 1000, toCall = 1000, streetBet = 0, chips = 1000, bigBlind = 20
        )
        val legal = LegalActions(canFold = true, canCheck = false, callAmount = 1000, minRaiseTo = null, maxRaiseTo = null)
        repeat(200) {
            val action = ai.decide(view, legal, Random(it))
            assertTrue(action is PlayerAction.Fold, "垃圾牌面对全下应弃牌")
        }
    }
}