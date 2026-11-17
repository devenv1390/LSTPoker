package com.aniapp.pokerdemo.vm

import com.aniapp.pokerdemo.engine.HeadsUpEngine
import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.Seat
import com.aniapp.pokerdemo.engine.ai.AiStrategy
import com.aniapp.pokerdemo.engine.ai.AiView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class CallBot : AiStrategy {
        override fun decide(view: AiView, legal: LegalActions, random: Random): PlayerAction =
            if (legal.canCheck) PlayerAction.Check else PlayerAction.Call
    }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = GameViewModel(
        engine = HeadsUpEngine(random = Random(1)),
        ai = CallBot(),
        aiRandom = Random(1)
    )

    @Test
    fun aiActsOnlyAfterDelay() = runTest(dispatcher.scheduler) {
        val vm = newVm()
        assertTrue(vm.uiState.value.isPlayerTurn)
        vm.onPlayerAction(PlayerAction.Call)
        assertEquals(Seat.AI, vm.uiState.value.game.acting)
        assertFalse(vm.uiState.value.aiThinking)
        advanceTimeBy(1500)
        runCurrent()
        assertEquals(Phase.FLOP, vm.uiState.value.game.phase)
        assertEquals(Seat.AI, vm.uiState.value.game.acting)
        advanceTimeBy(1500)
        runCurrent()
        assertEquals(Phase.FLOP, vm.uiState.value.game.phase)
        assertTrue(vm.uiState.value.isPlayerTurn)
        assertFalse(vm.uiState.value.aiThinking)
    }

    @Test
    fun playerActionIgnoredWhenNotTheirTurn() = runTest(dispatcher.scheduler) {
        val vm = newVm()
        vm.onPlayerAction(PlayerAction.Call)
        val before = vm.uiState.value.game
        vm.onPlayerAction(PlayerAction.Fold)
        assertEquals(before, vm.uiState.value.game)
    }

    @Test
    fun allInRunoutCompletesWithoutHang() = runTest(dispatcher.scheduler) {
        val vm = newVm()
        vm.onPlayerAction(PlayerAction.RaiseTo(2000))
        advanceUntilIdle()
        val s = vm.uiState.value.game
        assertTrue(s.phase == Phase.HAND_OVER || s.phase == Phase.MATCH_OVER)
        assertEquals(5, s.board.size)
        assertFalse(vm.uiState.value.aiThinking)
    }

    @Test
    fun newMatchCancelsPendingAi() = runTest(dispatcher.scheduler) {
        val vm = newVm()
        vm.onPlayerAction(PlayerAction.Call)
        advanceTimeBy(300)
        vm.onNewMatch()
        advanceUntilIdle()
        val s = vm.uiState.value.game
        assertEquals(1, s.handNumber)
        assertEquals(Phase.PREFLOP, s.phase)
        assertTrue(vm.uiState.value.isPlayerTurn)
        assertEquals(1990, s.player.chips)
    }

    @Test
    fun nextHandAdvancesDealer() = runTest(dispatcher.scheduler) {
        val vm = newVm()
        vm.onPlayerAction(PlayerAction.Fold)
        assertEquals(Phase.HAND_OVER, vm.uiState.value.game.phase)
        vm.onNextHand()
        assertEquals(Seat.AI, vm.uiState.value.game.dealer)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isPlayerTurn)
    }
}