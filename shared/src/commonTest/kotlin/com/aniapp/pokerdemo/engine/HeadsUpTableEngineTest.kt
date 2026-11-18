package com.aniapp.pokerdemo.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeadsUpTableEngineTest {

    private fun assertConserved(engine: TableEngine, total: Int = 4000) {
        val s = engine.state
        assertEquals(total, s.players.sumOf { it.chips + it.totalCommitted })
    }

    @Test
    fun sbFoldReturnsUncalledToBigBlind() {
        val engine = huEngine()
        engine.startMatch()
        engine.startHand()
        assertEquals(0, engine.state.dealer)
        assertEquals(0, engine.state.acting)
        val events = engine.act(0, PlayerAction.Fold)
        assertEquals(Phase.HAND_OVER, engine.state.phase)
        assertEquals(1990, engine.state.of(0).chips)
        assertEquals(2010, engine.state.of(1).chips)
        assertTrue(events.any { it is GameEvent.UncalledReturned && it.seat == 1 && it.amount == 10 })
        assertEquals(listOf(1), engine.state.result!!.awards.single().winners)
        assertEquals(EndReason.FOLD, engine.state.result!!.reason)
        assertEquals(0, engine.state.pot)
        assertTrue(!engine.state.showdown)
    }

    @Test
    fun limpGivesBbOptionAndPostflopNonDealerFirst() {
        val engine = huEngine()
        engine.startMatch()
        engine.startHand()
        engine.act(0, PlayerAction.Call)
        assertEquals(1, engine.state.acting)
        assertEquals(Phase.PREFLOP, engine.state.phase)
        val legal = engine.legalActions(1)
        assertTrue(legal.canCheck)
        assertTrue(legal.canRaise)
        engine.act(1, PlayerAction.Check)
        assertEquals(Phase.FLOP, engine.state.phase)
        assertEquals(3, engine.state.board.size)
        assertEquals(1, engine.state.acting)
        assertEquals(40, engine.state.pot)
    }

    @Test
    fun minRaiseProgression() {
        val engine = huEngine()
        engine.startMatch()
        engine.startHand()
        assertEquals(40, engine.legalActions(0).minRaiseTo)
        engine.act(0, PlayerAction.RaiseTo(40))
        assertEquals(60, engine.legalActions(1).minRaiseTo)
        engine.act(1, PlayerAction.RaiseTo(100))
        assertEquals(160, engine.legalActions(0).minRaiseTo)
        engine.act(0, PlayerAction.Call)
        assertEquals(Phase.FLOP, engine.state.phase)
        assertEquals(0, engine.state.currentBet)
        assertEquals(200, engine.state.pot)
        assertConserved(engine)
    }

    @Test
    fun checkDownShowdownSplitPot() {
        val engine = huEngine(newDeck = { stackedDeck("2h 3h", "4d 5d", board = "As Ks Qs Js Ts") })
        engine.startMatch()
        engine.startHand()
        engine.act(0, PlayerAction.Call)
        engine.act(1, PlayerAction.Check)
        repeat(3) {
            engine.act(1, PlayerAction.Check)
            engine.act(0, PlayerAction.Check)
        }
        assertEquals(Phase.HAND_OVER, engine.state.phase)
        val award = engine.state.result!!.awards.single()
        assertEquals(listOf(0, 1), award.winners.sorted())
        assertEquals(EndReason.SHOWDOWN, engine.state.result!!.reason)
        assertEquals(2000, engine.state.of(0).chips)
        assertEquals(2000, engine.state.of(1).chips)
        assertTrue(engine.state.showdown)
        assertEquals(HandCategory.STRAIGHT_FLUSH, engine.state.result!!.revealed.getValue(0).category)
        assertEquals(HandCategory.STRAIGHT_FLUSH, engine.state.result!!.revealed.getValue(1).category)
    }

    @Test
    fun allInPreflopRunsOutToShowdown() {
        val engine = huEngine(random = Random(2))
        engine.startMatch()
        engine.startHand()
        engine.act(0, PlayerAction.RaiseTo(2000))
        assertTrue(engine.state.of(0).isAllIn)
        engine.act(1, PlayerAction.Call)
        assertEquals(Phase.MATCH_OVER, engine.state.phase)
        assertEquals(5, engine.state.board.size)
        assertTrue(engine.state.showdown)
        assertEquals(4000, engine.state.players.sumOf { it.chips })
        assertTrue(engine.state.of(0).chips == 4000 || engine.state.of(1).chips == 4000)
    }

    @Test
    fun excessAllInIsRefunded() {
        val engine = huEngine(newDeck = { stackedDeck("As Ah", "2h 3h", board = "Ks Qd Jc 9h 7s") })
        engine.startMatch()
        engine.startHand()
        engine.act(0, PlayerAction.RaiseTo(1200))
        engine.act(1, PlayerAction.Call)
        repeat(3) {
            engine.act(1, PlayerAction.Check)
            engine.act(0, PlayerAction.Check)
        }
        assertEquals(3200, engine.state.of(0).chips)
        assertEquals(800, engine.state.of(1).chips)

        engine.startHand()
        assertEquals(1, engine.state.dealer)
        engine.act(1, PlayerAction.RaiseTo(800))
        assertTrue(engine.state.of(1).isAllIn)
        val legal = engine.legalActions(0)
        assertEquals(3200, legal.maxRaiseTo)
        val events = engine.act(0, PlayerAction.RaiseTo(3200))
        assertTrue(events.any { it is GameEvent.UncalledReturned && it.seat == 0 && it.amount == 2400 })
        assertTrue(events.any { it is GameEvent.PotAwarded && it.amount == 1600 })
        assertEquals(5, engine.state.board.size)
        assertEquals(4000, engine.state.players.sumOf { it.chips })
    }

    @Test
    fun shortAllInDoesNotReopenAction() {
        val engine = huEngine(GameConfig(startingChips = 100))
        engine.startMatch()
        engine.startHand()
        engine.act(0, PlayerAction.RaiseTo(70))
        val aiLegal = engine.legalActions(1)
        assertEquals(100, aiLegal.minRaiseTo)
        assertEquals(100, aiLegal.maxRaiseTo)
        engine.act(1, PlayerAction.RaiseTo(100))
        assertTrue(engine.state.of(1).isAllIn)
        val pLegal = engine.legalActions(0)
        assertNull(pLegal.minRaiseTo)
        assertEquals(30, pLegal.callAmount)
        engine.act(0, PlayerAction.Call)
        assertEquals(5, engine.state.board.size)
        assertTrue(engine.state.phase == Phase.HAND_OVER || engine.state.phase == Phase.MATCH_OVER)
        assertConserved(engine, 200)
    }

    @Test
    fun shortStackBlindsAllInAutoRunout() {
        val engine = huEngine(GameConfig(startingChips = 5))
        engine.startMatch()
        val events = engine.startHand()
        assertEquals(Phase.MATCH_OVER, engine.state.phase)
        assertEquals(5, engine.state.board.size)
        assertEquals(10, engine.state.players.sumOf { it.chips })
        assertTrue(events.any { it is GameEvent.HandFinished })

        val e2 = huEngine(GameConfig(startingChips = 15))
        e2.startMatch()
        e2.startHand()
        assertEquals(0, e2.state.acting)
        assertEquals(5, e2.legalActions(0).callAmount)
        e2.act(0, PlayerAction.Call)
        assertEquals(5, e2.state.board.size)
        assertConserved(e2, 30)
    }

    @Test
    fun dealerRotationAndMatchReset() {
        val engine = huEngine(random = Random(3))
        engine.startMatch()
        engine.startHand()
        assertEquals(0, engine.state.dealer)
        engine.act(0, PlayerAction.Fold)
        assertEquals(Phase.HAND_OVER, engine.state.phase)
        engine.startHand()
        assertEquals(1, engine.state.dealer)
        assertEquals(1, engine.state.acting)

        val e2 = huEngine(GameConfig(startingChips = 20))
        e2.startMatch()
        e2.startHand()
        e2.act(0, PlayerAction.Call)
        assertEquals(Phase.MATCH_OVER, e2.state.phase)
        e2.startMatch()
        assertEquals(Phase.IDLE, e2.state.phase)
        assertEquals(20, e2.state.of(0).chips)
        assertEquals(20, e2.state.of(1).chips)
        assertEquals(0, e2.state.handNumber)
    }

    @Test
    fun illegalActionsThrow() {
        val engine = huEngine()
        engine.startMatch()
        engine.startHand()
        assertFailsWith<IllegalStateException> { engine.act(1, PlayerAction.Call) }
        assertFailsWith<IllegalStateException> { engine.act(0, PlayerAction.Check) }
        assertFailsWith<IllegalStateException> { engine.act(0, PlayerAction.RaiseTo(30)) }
        engine.act(0, PlayerAction.Fold)
        assertFailsWith<IllegalStateException> { engine.act(0, PlayerAction.Call) }
    }

    private fun randomAction(legal: LegalActions, random: Random): PlayerAction {
        if (legal.canFold && random.nextDouble() < 0.08) return PlayerAction.Fold
        val options = mutableListOf<PlayerAction>()
        if (legal.canCheck) options += PlayerAction.Check
        if (legal.canCall) options += PlayerAction.Call
        if (legal.canRaise) {
            val min = legal.minRaiseTo!!
            val max = legal.maxRaiseTo!!
            options += PlayerAction.RaiseTo(if (max == min) min else random.nextInt(min, max + 1))
        }
        return options[random.nextInt(options.size)]
    }

    @Test
    fun chipConservationStress500Hands() {
        val random = Random(7)
        val engine = huEngine(random = random)
        engine.startMatch()
        repeat(500) {
            engine.startHand()
            var guard = 0
            while (engine.state.phase.isBetting()) {
                check(++guard < 200) { "下注轮死循环" }
                val seat = engine.state.acting!!
                engine.act(seat, randomAction(engine.legalActions(seat), random))
                assertConserved(engine)
            }
            val s = engine.state
            assertEquals(4000, s.players.sumOf { it.chips })
            assertEquals(0, s.pot)
            assertTrue(s.phase == Phase.HAND_OVER || s.phase == Phase.MATCH_OVER)
            if (s.phase == Phase.MATCH_OVER) engine.startMatch()
        }
    }
}