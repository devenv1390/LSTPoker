package com.aniapp.pokerdemo.engine.ai

import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.GameState
import com.aniapp.pokerdemo.engine.HeadsUpEngine
import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.Seat
import com.aniapp.pokerdemo.engine.Suit
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RuleBasedAiTest {

    private val ai = RuleBasedAi()

    private fun cards(s: String): List<Card> = s.split(" ").map { tok ->
        val rank = when (val c = tok[0]) {
            'A' -> 14; 'K' -> 13; 'Q' -> 12; 'J' -> 11; 'T' -> 10
            else -> c.digitToInt()
        }
        val suit = when (tok[1]) {
            's' -> Suit.SPADES; 'h' -> Suit.HEARTS; 'd' -> Suit.DIAMONDS; else -> Suit.CLUBS
        }
        Card(rank, suit)
    }

    private fun assertLegal(legal: LegalActions, action: PlayerAction) {
        when (action) {
            PlayerAction.Fold -> assertTrue("非法 Fold", legal.canFold)
            PlayerAction.Check -> assertTrue("非法 Check", legal.canCheck)
            PlayerAction.Call -> assertTrue("非法 Call", legal.canCall)
            is PlayerAction.RaiseTo -> {
                assertTrue("非法 Raise", legal.canRaise)
                assertTrue("加注额越界: ${action.amount}", action.amount in legal.minRaiseTo!!..legal.maxRaiseTo!!)
            }
        }
    }

    private fun viewOf(state: GameState, seat: Seat, legal: LegalActions): AiView {
        val me = state.of(seat)
        return AiView(me.hole, state.board, state.phase, state.pot, legal.callAmount, me.streetBet, me.chips, 20)
    }

    @Test
    fun alwaysLegalInRandomGameStates() {
        val random = Random(11)
        val engine = HeadsUpEngine(random = random)
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
        assertTrue("样本不足: $checked", checked >= 200)
    }

    @Test
    fun nutsNeverFolds() {
        val view = AiView(
            hole = cards("2h 3h"),
            board = cards("As Ks Qs Js Ts"),
            phase = Phase.RIVER,
            pot = 400, toCall = 200, streetBet = 0, chips = 1000, bigBlind = 20
        )
        val legal = LegalActions(canFold = true, canCheck = false, callAmount = 200, minRaiseTo = 400, maxRaiseTo = 1000)
        val random = Random(5)
        repeat(200) {
            val action = ai.decide(view, legal, random)
            assertTrue("坚果牌不应弃牌", action !is PlayerAction.Fold)
            assertLegal(legal, action)
        }
    }

    @Test
    fun junkFoldsToShove() {
        val view = AiView(
            hole = cards("2h 7c"),
            board = cards("As Kd Qh"),
            phase = Phase.FLOP,
            pot = 1000, toCall = 1000, streetBet = 0, chips = 1000, bigBlind = 20
        )
        val legal = LegalActions(canFold = true, canCheck = false, callAmount = 1000, minRaiseTo = null, maxRaiseTo = null)
        repeat(200) {
            val action = ai.decide(view, legal, Random(it))
            assertTrue("垃圾牌面对全下应弃牌", action is PlayerAction.Fold)
        }
    }
}