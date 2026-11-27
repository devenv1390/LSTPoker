package com.aniapp.pokerdemo.analysis

import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.Suit
import com.aniapp.pokerdemo.engine.ai.mc.EquityResult
import com.aniapp.pokerdemo.net.SeatInfo
import com.aniapp.pokerdemo.net.TableView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class AssistanceControllerTest {

    @Test
    fun reusesEquityWhenOnlyPotChanges() = runTest {
        val analyzer = CountingAnalyzer()
        val controller = AssistanceController(analyzer, StandardTestDispatcher(testScheduler))
        try {
            controller.update(view(pot = 300), bigBlind = 20, enabled = true)
            runCurrent()
            assertIs<AssistanceState.Ready>(controller.state.value)

            controller.update(view(pot = 500), bigBlind = 20, enabled = true)
            runCurrent()
            assertIs<AssistanceState.Ready>(controller.state.value)

            assertEquals(1, analyzer.equityCalculations)
        } finally {
            controller.dispose()
        }
    }

    @Test
    fun repeatedIdenticalUpdateDoesNotRecalculate() = runTest {
        val analyzer = CountingAnalyzer()
        val controller = AssistanceController(analyzer, StandardTestDispatcher(testScheduler))
        try {
            repeat(3) { controller.update(view(), bigBlind = 20, enabled = true) }
            runCurrent()

            assertEquals(1, analyzer.equityCalculations)
        } finally {
            controller.dispose()
        }
    }

    @Test
    fun usesLatestPotWhenEquityFinishesAfterAdviceChange() = runTest {
        val analyzer = ControlledAnalyzer()
        val controller = AssistanceController(analyzer, StandardTestDispatcher(testScheduler))
        try {
            controller.update(view(pot = 300), bigBlind = 20, enabled = true)
            runCurrent()
            controller.update(view(pot = 500), bigBlind = 20, enabled = true)
            runCurrent()

            assertEquals(1, analyzer.gates.size)
            analyzer.gates.single().complete(Unit)
            runCurrent()

            val ready = assertIs<AssistanceState.Ready>(controller.state.value)
            assertEquals(assertNotNull(AdviceSituation.from(view(pot = 500), 20)).adviceKey, ready.analysis.adviceKey)
        } finally {
            controller.dispose()
        }
    }

    @Test
    fun newBoardCancelsOldCalculationAndNeverPublishesIt() = runTest {
        val analyzer = ControlledAnalyzer()
        val controller = AssistanceController(analyzer, StandardTestDispatcher(testScheduler))
        try {
            controller.update(view(), bigBlind = 20, enabled = true)
            runCurrent()
            val changed = view(hole = listOf(Card(8, Suit.HEARTS), Card(7, Suit.CLUBS)))
            controller.update(changed, bigBlind = 20, enabled = true)
            runCurrent()

            assertEquals(1, analyzer.cancellations)
            assertEquals(2, analyzer.gates.size)
            analyzer.gates[0].complete(Unit)
            runCurrent()
            assertIs<AssistanceState.Loading>(controller.state.value)

            analyzer.gates[1].complete(Unit)
            runCurrent()
            val ready = assertIs<AssistanceState.Ready>(controller.state.value)
            assertEquals(assertNotNull(AdviceSituation.from(changed, 20)).adviceKey, ready.analysis.adviceKey)
        } finally {
            controller.dispose()
        }
    }

    @Test
    fun disablingCancelsCalculationAndReturnsOff() = runTest {
        val analyzer = ControlledAnalyzer()
        val controller = AssistanceController(analyzer, StandardTestDispatcher(testScheduler))
        try {
            controller.update(view(), bigBlind = 20, enabled = true)
            runCurrent()
            controller.update(view(), bigBlind = 20, enabled = false)
            runCurrent()

            assertEquals(1, analyzer.cancellations)
            assertIs<AssistanceState.Off>(controller.state.value)
        } finally {
            controller.dispose()
        }
    }

    @Test
    fun invalidSituationIsUnavailableWithoutCalculation() = runTest {
        val analyzer = CountingAnalyzer()
        val controller = AssistanceController(analyzer, StandardTestDispatcher(testScheduler))
        try {
            controller.update(view(hole = listOf(Card(7, Suit.HEARTS), Card(7, Suit.HEARTS))), 20, true)
            runCurrent()

            assertEquals(0, analyzer.equityCalculations)
            assertIs<AssistanceState.Unavailable>(controller.state.value)
        } finally {
            controller.dispose()
        }
    }

    @Test
    fun calculationFailureIsIsolatedFromGame() = runTest {
        val controller = AssistanceController(FailingAnalyzer(), StandardTestDispatcher(testScheduler))
        try {
            controller.update(view(), bigBlind = 20, enabled = true)
            runCurrent()

            assertIs<AssistanceState.Unavailable>(controller.state.value)
        } finally {
            controller.dispose()
        }
    }

    @Test
    fun cacheEvictsOldEquityWhenCapacityIsReached() = runTest {
        val analyzer = CountingAnalyzer()
        val controller = AssistanceController(analyzer, StandardTestDispatcher(testScheduler), maxCacheSize = 1)
        try {
            val first = view()
            val second = view(hole = listOf(Card(8, Suit.HEARTS), Card(7, Suit.CLUBS)))
            controller.update(first, 20, true)
            runCurrent()
            controller.update(second, 20, true)
            runCurrent()
            controller.update(first, 20, true)
            runCurrent()

            assertEquals(3, analyzer.equityCalculations)
        } finally {
            controller.dispose()
        }
    }

    private class CountingAnalyzer : AssistanceAnalyzer() {
        var equityCalculations = 0

        override suspend fun calculateEquity(situation: AdviceSituation, ensureActive: () -> Unit): EquityResult {
            equityCalculations++
            return super.calculateEquity(situation, ensureActive)
        }
    }

    private class ControlledAnalyzer : AssistanceAnalyzer() {
        val gates = mutableListOf<CompletableDeferred<Unit>>()
        var cancellations = 0

        override suspend fun calculateEquity(situation: AdviceSituation, ensureActive: () -> Unit): EquityResult {
            val gate = CompletableDeferred<Unit>()
            gates += gate
            return try {
                gate.await()
                EquityResult(0.5, 1)
            } catch (cancelled: CancellationException) {
                cancellations++
                throw cancelled
            }
        }
    }

    private class FailingAnalyzer : AssistanceAnalyzer() {
        override suspend fun calculateEquity(situation: AdviceSituation, ensureActive: () -> Unit): EquityResult =
            throw IllegalStateException("analysis failed")
    }

    private fun view(
        pot: Int = 300,
        hole: List<Card> = listOf(Card(7, Suit.HEARTS), Card(7, Suit.CLUBS))
    ): TableView = TableView(
        seats = listOf(
            SeatInfo(0, "你", 1000, 0, 100, false, false, true, false),
            SeatInfo(1, "对手", 1000, 100, pot - 100, false, false, true, false)
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
        pot = pot,
        mySeat = 0,
        myHole = hole,
        legal = LegalActions(canFold = true, callAmount = 100, minRaiseTo = 200, maxRaiseTo = 1000)
    )
}
