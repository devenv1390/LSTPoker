package com.aniapp.pokerdemo.analysis

import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.Suit
import com.aniapp.pokerdemo.engine.ai.mc.EquityResult
import com.aniapp.pokerdemo.net.SeatInfo
import com.aniapp.pokerdemo.net.TableView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistanceAnalyzerTest {

    @Test
    fun analyzesRiverHandAndCallReferenceEv() {
        val situation = assertNotNull(AdviceSituation.from(riverView(), bigBlind = 20))
        val analysis = AssistanceAnalyzer().analyze(situation)

        assertEquals("四条", analysis.hand.label)
        assertEquals(1.0, analysis.equity.value)
        assertEquals(EquityMethod.EXACT_HEADS_UP_RIVER, analysis.equity.method)
        assertEquals(300.0, analysis.actions.single { it.action == PlayerAction.Call }.referenceEv)
    }

    @Test
    fun raiseReferenceEvAccountsForShortAllInOpponent() {
        val view = riverView().copy(
            seats = listOf(
                SeatInfo(0, "你", 1000, 0, 100, false, false, true, false),
                SeatInfo(1, "全下对手", 0, 100, 200, false, true, true, false),
                SeatInfo(2, "活跃对手", 1000, 100, 200, false, false, true, false)
            ),
            pot = 500
        )
        val situation = assertNotNull(AdviceSituation.from(view, bigBlind = 20))
        val analysis = AssistanceAnalyzer().analyze(situation, EquityResult(0.5, 100))

        val minRaise = analysis.actions.single { it.action == PlayerAction.RaiseTo(200) }
        assertEquals(200.0, minRaise.referenceEv)
    }

    @Test
    fun raiseReferenceEvReturnsUncalledAmountWhenNoOpponentCanMatch() {
        val view = riverView().copy(
            seats = listOf(
                SeatInfo(0, "你", 1000, 0, 0, false, false, true, false),
                SeatInfo(1, "全下对手", 0, 100, 100, false, true, true, false)
            ),
            currentBet = 100,
            pot = 100,
            legal = LegalActions(canFold = true, callAmount = 100, minRaiseTo = 200, maxRaiseTo = 1000)
        )
        val situation = assertNotNull(AdviceSituation.from(view, bigBlind = 20))
        val analysis = AssistanceAnalyzer().analyze(situation, EquityResult(0.5, 100))

        val raise = analysis.actions.single { it.action == PlayerAction.RaiseTo(200) }
        assertEquals(0.0, raise.referenceEv)
    }

    @Test
    fun preflopUsesPocketPairAndSuitedLabels() {
        val pair = preflopView(listOf(Card(13, Suit.HEARTS), Card(13, Suit.CLUBS)))
        val suited = preflopView(listOf(Card(14, Suit.HEARTS), Card(13, Suit.HEARTS)))

        assertEquals("口袋对 K", AssistanceAnalyzer().analyze(assertNotNull(AdviceSituation.from(pair, 20))).hand.label)
        assertEquals("A-K·同花", AssistanceAnalyzer().analyze(assertNotNull(AdviceSituation.from(suited, 20))).hand.label)
    }

    @Test
    fun flopReportsOpenEndedStraightDraw() {
        val view = riverView().copy(
            board = listOf(Card(7, Suit.DIAMONDS), Card(6, Suit.CLUBS), Card(2, Suit.HEARTS)),
            phase = Phase.FLOP,
            currentBet = 0,
            pot = 60,
            myHole = listOf(Card(8, Suit.HEARTS), Card(9, Suit.SPADES)),
            legal = LegalActions(canFold = true, canCheck = true, minRaiseTo = 40, maxRaiseTo = 1000)
        )
        val analysis = AssistanceAnalyzer().analyze(
            assertNotNull(AdviceSituation.from(view, 20)),
            EquityResult(0.40, 100)
        )

        assertEquals("高牌", analysis.hand.label)
        assertTrue("两端顺子听牌" in analysis.hand.draws)
        assertEquals(EquityMethod.MONTE_CARLO, analysis.equity.method)
        assertNotNull(analysis.equity.margin95)
    }

    @Test
    fun publicFingerprintIgnoresNamesConnectionAndRevealedHoles() {
        val first = riverView()
        val second = first.copy(
            seats = first.seats.map { it.copy(name = "匿名", connected = false, isAi = true) },
            revealedHoles = mapOf(1 to listOf(Card(14, Suit.SPADES), Card(14, Suit.HEARTS)))
        )
        val a = assertNotNull(AdviceSituation.from(first, 20))
        val b = assertNotNull(AdviceSituation.from(second, 20))

        assertEquals(a, b)
        assertEquals(a.equityKey, b.equityKey)
        assertEquals(a.adviceKey, b.adviceKey)
    }

    @Test
    fun rejectsDuplicateCardsAndMissingOpponents() {
        val duplicate = riverView().copy(
            myHole = listOf(Card(7, Suit.HEARTS), Card(7, Suit.SPADES))
        )
        val noOpponent = riverView().copy(seats = riverView().seats.take(1))

        assertNull(AdviceSituation.from(duplicate, 20))
        assertNull(AdviceSituation.from(noOpponent, 20))
    }

    @Test
    fun nonPlayerTurnHasNoActionEstimatesOrRecommendation() {
        val view = riverView().copy(acting = 1, legal = LegalActions())
        val analysis = AssistanceAnalyzer().analyze(
            assertNotNull(AdviceSituation.from(view, 20)),
            EquityResult(0.5, 100)
        )

        assertTrue(analysis.actions.isEmpty())
        assertNull(analysis.recommendation)
    }

    @Test
    fun recommendationsAndEstimatesStayInsideLegalActions() {
        val legalActions = listOf(
            LegalActions(canFold = true, canCheck = true, minRaiseTo = 40, maxRaiseTo = 1000),
            LegalActions(canFold = true, callAmount = 20, minRaiseTo = 60, maxRaiseTo = 1000),
            LegalActions(canFold = true, callAmount = 1000),
            LegalActions(canFold = true, callAmount = 100, minRaiseTo = 200, maxRaiseTo = 1000)
        )
        for (legal in legalActions) {
            for (equity in listOf(0.05, 0.30, 0.55, 0.90)) {
                val view = riverView().copy(
                    phase = Phase.FLOP,
                    board = listOf(Card(7, Suit.SPADES), Card(7, Suit.DIAMONDS), Card(2, Suit.HEARTS)),
                    currentBet = legal.callAmount,
                    legal = legal
                )
                val situation = assertNotNull(AdviceSituation.from(view, 20))
                val analysis = AssistanceAnalyzer().analyze(situation, EquityResult(equity, 100))
                val recommendation = assertNotNull(analysis.recommendation)
                assertTrue(legal.accepts(recommendation.action), "非法推荐: ${recommendation.action}")
                assertTrue(analysis.actions.all { legal.accepts(it.action) }, "非法动作估算: ${analysis.actions}")
            }
        }
    }

    @Test
    fun samePublicSituationProducesStableMonteCarloAdvice() {
        val situation = assertNotNull(AdviceSituation.from(preflopView(), 20))
        val analyzer = AssistanceAnalyzer()
        val first = analyzer.analyze(situation)
        val second = analyzer.analyze(situation)

        assertEquals(first.equity, second.equity)
        assertEquals(first.recommendation, second.recommendation)
    }

    private fun riverView(): TableView = TableView(
        seats = listOf(
            SeatInfo(0, "你", 1000, 0, 100, false, false, true, false),
            SeatInfo(1, "对手", 1000, 100, 200, false, false, true, false)
        ),
        board = listOf(
            Card(7, Suit.SPADES), Card(7, Suit.DIAMONDS), Card(2, Suit.HEARTS),
            Card(9, Suit.CLUBS), Card(11, Suit.DIAMONDS)
        ),
        dealer = 1,
        acting = 0,
        phase = Phase.RIVER,
        handNumber = 4,
        currentBet = 100,
        minRaiseTo = 200,
        pot = 300,
        mySeat = 0,
        myHole = listOf(Card(7, Suit.HEARTS), Card(7, Suit.CLUBS)),
        legal = LegalActions(canFold = true, callAmount = 100, minRaiseTo = 200, maxRaiseTo = 1000)
    )

    private fun preflopView(
        hole: List<Card> = listOf(Card(14, Suit.HEARTS), Card(13, Suit.CLUBS))
    ): TableView = riverView().copy(
        seats = listOf(
            SeatInfo(0, "你", 1000, 0, 0, false, false, true, false),
            SeatInfo(1, "对手", 1000, 20, 20, false, false, true, false)
        ),
        board = emptyList(),
        phase = Phase.PREFLOP,
        currentBet = 20,
        pot = 20,
        myHole = hole,
        legal = LegalActions(canFold = true, callAmount = 20, minRaiseTo = 40, maxRaiseTo = 1000)
    )
}
