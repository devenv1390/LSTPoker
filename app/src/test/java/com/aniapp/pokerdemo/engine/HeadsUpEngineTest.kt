package com.aniapp.pokerdemo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HeadsUpEngineTest {

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

    // 发牌顺序：玩家2张、AI2张、烧牌、翻牌3、烧牌、转牌、烧牌、河牌
    private fun stackedDeck(playerHole: String, aiHole: String, board: String): Deck {
        val b = cards(board)
        val list = cards(playerHole) + cards(aiHole) +
            listOf(Card(2, Suit.SPADES)) + b.take(3) +
            listOf(Card(3, Suit.SPADES)) + listOf(b[3]) +
            listOf(Card(4, Suit.SPADES)) + listOf(b[4])
        return Deck(list)
    }

    private fun assertConserved(engine: HeadsUpEngine, total: Int = 4000) {
        val s = engine.state
        assertEquals(total, s.player.chips + s.ai.chips + s.player.totalCommitted + s.ai.totalCommitted)
    }

    @Test
    fun sbFoldReturnsUncalledToBigBlind() {
        val engine = HeadsUpEngine(random = Random(1))
        engine.startMatch()
        engine.startHand()
        assertEquals(Seat.PLAYER, engine.state.dealer)
        assertEquals(Seat.PLAYER, engine.state.acting)
        val events = engine.act(Seat.PLAYER, PlayerAction.Fold)
        assertEquals(Phase.HAND_OVER, engine.state.phase)
        assertEquals(1990, engine.state.player.chips)
        assertEquals(2010, engine.state.ai.chips)
        assertTrue(events.any { it is GameEvent.UncalledReturned && it.seat == Seat.AI && it.amount == 10 })
        assertEquals(Seat.AI, engine.state.result!!.winner)
        assertEquals(EndReason.FOLD, engine.state.result!!.reason)
        assertEquals(0, engine.state.pot)
        assertTrue(!engine.state.aiRevealed)
    }

    @Test
    fun limpGivesBbOptionAndPostflopNonDealerFirst() {
        val engine = HeadsUpEngine(random = Random(1))
        engine.startMatch()
        engine.startHand()
        engine.act(Seat.PLAYER, PlayerAction.Call)
        assertEquals(Seat.AI, engine.state.acting)
        assertEquals(Phase.PREFLOP, engine.state.phase)
        val legal = engine.legalActions(Seat.AI)
        assertTrue(legal.canCheck)
        assertTrue(legal.canRaise)
        engine.act(Seat.AI, PlayerAction.Check)
        assertEquals(Phase.FLOP, engine.state.phase)
        assertEquals(3, engine.state.board.size)
        assertEquals(Seat.AI, engine.state.acting)
        assertEquals(40, engine.state.pot)
    }

    @Test
    fun minRaiseProgression() {
        val engine = HeadsUpEngine(random = Random(1))
        engine.startMatch()
        engine.startHand()
        assertEquals(40, engine.legalActions(Seat.PLAYER).minRaiseTo)
        engine.act(Seat.PLAYER, PlayerAction.RaiseTo(40))
        assertEquals(60, engine.legalActions(Seat.AI).minRaiseTo)
        engine.act(Seat.AI, PlayerAction.RaiseTo(100))
        assertEquals(160, engine.legalActions(Seat.PLAYER).minRaiseTo)
        engine.act(Seat.PLAYER, PlayerAction.Call)
        assertEquals(Phase.FLOP, engine.state.phase)
        assertEquals(0, engine.state.currentBet)
        assertEquals(200, engine.state.pot)
        assertConserved(engine)
    }

    @Test
    fun checkDownShowdownSplitPot() {
        val engine = HeadsUpEngine(
            random = Random(1),
            newDeck = { stackedDeck("2h 3h", "4d 5d", "As Ks Qs Js Ts") }
        )
        engine.startMatch()
        engine.startHand()
        engine.act(Seat.PLAYER, PlayerAction.Call)
        engine.act(Seat.AI, PlayerAction.Check)
        repeat(3) {
            engine.act(Seat.AI, PlayerAction.Check)
            engine.act(Seat.PLAYER, PlayerAction.Check)
        }
        assertEquals(Phase.HAND_OVER, engine.state.phase)
        assertNull(engine.state.result!!.winner)
        assertEquals(EndReason.SHOWDOWN, engine.state.result!!.reason)
        assertEquals(2000, engine.state.player.chips)
        assertEquals(2000, engine.state.ai.chips)
        assertTrue(engine.state.aiRevealed)
        assertEquals(HandCategory.STRAIGHT_FLUSH, engine.state.result!!.playerValue!!.category)
        assertEquals(HandCategory.STRAIGHT_FLUSH, engine.state.result!!.aiValue!!.category)
    }

    @Test
    fun allInPreflopRunsOutToShowdown() {
        val engine = HeadsUpEngine(random = Random(2))
        engine.startMatch()
        engine.startHand()
        engine.act(Seat.PLAYER, PlayerAction.RaiseTo(2000))
        assertTrue(engine.state.player.isAllIn)
        engine.act(Seat.AI, PlayerAction.Call)
        assertEquals(Phase.MATCH_OVER, engine.state.phase)
        assertEquals(5, engine.state.board.size)
        assertTrue(engine.state.aiRevealed)
        assertEquals(4000, engine.state.player.chips + engine.state.ai.chips)
        assertTrue(engine.state.player.chips == 4000 || engine.state.ai.chips == 4000)
    }

    @Test
    fun excessAllInIsRefunded() {
        val engine = HeadsUpEngine(
            random = Random(1),
            newDeck = { stackedDeck("As Ah", "2h 3h", "Ks Qd Jc 9h 7s") }
        )
        engine.startMatch()
        engine.startHand()
        engine.act(Seat.PLAYER, PlayerAction.RaiseTo(1200))
        engine.act(Seat.AI, PlayerAction.Call)
        repeat(3) {
            engine.act(Seat.AI, PlayerAction.Check)
            engine.act(Seat.PLAYER, PlayerAction.Check)
        }
        assertEquals(3200, engine.state.player.chips)
        assertEquals(800, engine.state.ai.chips)

        engine.startHand()
        assertEquals(Seat.AI, engine.state.dealer)
        engine.act(Seat.AI, PlayerAction.RaiseTo(800))
        assertTrue(engine.state.ai.isAllIn)
        val legal = engine.legalActions(Seat.PLAYER)
        assertEquals(3200, legal.maxRaiseTo)
        val events = engine.act(Seat.PLAYER, PlayerAction.RaiseTo(3200))
        assertTrue(events.any { it is GameEvent.UncalledReturned && it.seat == Seat.PLAYER && it.amount == 2400 })
        assertTrue(events.any { it is GameEvent.PotAwarded && it.amount == 1600 })
        assertEquals(5, engine.state.board.size)
        assertEquals(4000, engine.state.player.chips + engine.state.ai.chips)
    }

    @Test
    fun shortAllInDoesNotReopenAction() {
        val engine = HeadsUpEngine(GameConfig(startingChips = 100), random = Random(1))
        engine.startMatch()
        engine.startHand()
        engine.act(Seat.PLAYER, PlayerAction.RaiseTo(70))
        val aiLegal = engine.legalActions(Seat.AI)
        assertEquals(100, aiLegal.minRaiseTo)
        assertEquals(100, aiLegal.maxRaiseTo)
        engine.act(Seat.AI, PlayerAction.RaiseTo(100))
        assertTrue(engine.state.ai.isAllIn)
        val pLegal = engine.legalActions(Seat.PLAYER)
        assertNull(pLegal.minRaiseTo)
        assertEquals(30, pLegal.callAmount)
        engine.act(Seat.PLAYER, PlayerAction.Call)
        assertEquals(5, engine.state.board.size)
        assertTrue(engine.state.phase == Phase.HAND_OVER || engine.state.phase == Phase.MATCH_OVER)
        assertConserved(engine, 200)
    }

    @Test
    fun shortStackBlindsAllInAutoRunout() {
        val engine = HeadsUpEngine(GameConfig(startingChips = 5), random = Random(1))
        engine.startMatch()
        val events = engine.startHand()
        assertEquals(Phase.MATCH_OVER, engine.state.phase)
        assertEquals(5, engine.state.board.size)
        assertEquals(10, engine.state.player.chips + engine.state.ai.chips)
        assertTrue(events.any { it is GameEvent.HandFinished })

        val e2 = HeadsUpEngine(GameConfig(startingChips = 15), random = Random(1))
        e2.startMatch()
        e2.startHand()
        assertEquals(Seat.PLAYER, e2.state.acting)
        assertEquals(5, e2.legalActions(Seat.PLAYER).callAmount)
        e2.act(Seat.PLAYER, PlayerAction.Call)
        assertEquals(5, e2.state.board.size)
        assertConserved(e2, 30)
    }

    @Test
    fun dealerRotationAndMatchReset() {
        val engine = HeadsUpEngine(random = Random(3))
        engine.startMatch()
        engine.startHand()
        assertEquals(Seat.PLAYER, engine.state.dealer)
        engine.act(Seat.PLAYER, PlayerAction.Fold)
        assertEquals(Phase.HAND_OVER, engine.state.phase)
        engine.startHand()
        assertEquals(Seat.AI, engine.state.dealer)
        assertEquals(Seat.AI, engine.state.acting)

        val e2 = HeadsUpEngine(GameConfig(startingChips = 20), random = Random(1))
        e2.startMatch()
        e2.startHand()
        e2.act(Seat.PLAYER, PlayerAction.Call)
        assertEquals(Phase.MATCH_OVER, e2.state.phase)
        e2.startMatch()
        assertEquals(Phase.IDLE, e2.state.phase)
        assertEquals(20, e2.state.player.chips)
        assertEquals(20, e2.state.ai.chips)
        assertEquals(0, e2.state.handNumber)
    }

    @Test
    fun illegalActionsThrow() {
        val engine = HeadsUpEngine(random = Random(1))
        engine.startMatch()
        engine.startHand()
        assertThrows(IllegalStateException::class.java) { engine.act(Seat.AI, PlayerAction.Call) }
        assertThrows(IllegalStateException::class.java) { engine.act(Seat.PLAYER, PlayerAction.Check) }
        assertThrows(IllegalStateException::class.java) { engine.act(Seat.PLAYER, PlayerAction.RaiseTo(30)) }
        engine.act(Seat.PLAYER, PlayerAction.Fold)
        assertThrows(IllegalStateException::class.java) { engine.act(Seat.PLAYER, PlayerAction.Call) }
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
        val engine = HeadsUpEngine(random = random)
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
            assertEquals(4000, s.player.chips + s.ai.chips)
            assertEquals(0, s.pot)
            assertTrue(s.phase == Phase.HAND_OVER || s.phase == Phase.MATCH_OVER)
            if (s.phase == Phase.MATCH_OVER) engine.startMatch()
        }
    }
}