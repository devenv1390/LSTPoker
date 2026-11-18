package com.aniapp.pokerdemo.session

import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.PlayerInit
import com.aniapp.pokerdemo.engine.TableEngine
import com.aniapp.pokerdemo.engine.ai.AiStrategy
import com.aniapp.pokerdemo.engine.ai.AiView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocalSessionTest {

    private val dispatcher = StandardTestDispatcher()

    private class CallBot : AiStrategy {
        override fun decide(view: AiView, legal: LegalActions, random: Random): PlayerAction =
            if (legal.canCheck) PlayerAction.Check else PlayerAction.Call
    }

    private fun newSession(scope: CoroutineScope) = LocalSession(
        scope = scope,
        engine = TableEngine(players = listOf(PlayerInit("你"), PlayerInit("电脑")), random = Random(1)),
        ai = CallBot(),
        aiRandom = Random(1),
        // AI 决策默认在 Dispatchers.Default 上跑（真实线程），测试注入调度器以保持虚拟时间确定性
        aiDispatcher = dispatcher
    )

    @Test
    fun aiActsOnlyAfterDelay() = runTest(dispatcher.scheduler) {
        val session = newSession(backgroundScope)
        assertTrue(session.uiState.value.isPlayerTurn)
        session.act(PlayerAction.Call)
        assertEquals(1, session.uiState.value.table.acting)
        assertFalse(session.uiState.value.aiThinking)
        advanceTimeBy(1500)
        runCurrent()
        assertEquals(Phase.FLOP, session.uiState.value.table.phase)
        assertEquals(1, session.uiState.value.table.acting)
        advanceTimeBy(1500)
        runCurrent()
        assertEquals(Phase.FLOP, session.uiState.value.table.phase)
        assertTrue(session.uiState.value.isPlayerTurn)
        assertFalse(session.uiState.value.aiThinking)
    }

    @Test
    fun playerActionIgnoredWhenNotTheirTurn() = runTest(dispatcher.scheduler) {
        val session = newSession(backgroundScope)
        session.act(PlayerAction.Call)
        val before = session.uiState.value.table
        session.act(PlayerAction.Fold)
        assertEquals(before, session.uiState.value.table)
    }

    @Test
    fun allInRunoutCompletesWithoutHang() = runTest(dispatcher.scheduler) {
        val session = newSession(backgroundScope)
        session.act(PlayerAction.RaiseTo(2000))
        advanceTimeBy(30_000)
        runCurrent()
        val table = session.uiState.value.table
        assertTrue(table.phase == Phase.HAND_OVER || table.phase == Phase.MATCH_OVER)
        assertEquals(5, table.board.size)
        assertFalse(session.uiState.value.aiThinking)
    }

    @Test
    fun newMatchCancelsPendingAi() = runTest(dispatcher.scheduler) {
        val session = newSession(backgroundScope)
        session.act(PlayerAction.Call)
        advanceTimeBy(300)
        session.newMatch()
        advanceTimeBy(30_000)
        runCurrent()
        val table = session.uiState.value.table
        assertEquals(1, table.handNumber)
        assertEquals(Phase.PREFLOP, table.phase)
        assertTrue(session.uiState.value.isPlayerTurn)
        assertEquals(1990, table.seats[0].chips)
    }

    @Test
    fun nextHandAdvancesDealer() = runTest(dispatcher.scheduler) {
        val session = newSession(backgroundScope)
        session.act(PlayerAction.Fold)
        assertEquals(Phase.HAND_OVER, session.uiState.value.table.phase)
        session.nextHand()
        assertEquals(1, session.uiState.value.table.dealer)
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(session.uiState.value.isPlayerTurn)
    }
}