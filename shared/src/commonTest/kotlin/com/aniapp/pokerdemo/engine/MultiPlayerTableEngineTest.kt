package com.aniapp.pokerdemo.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MultiPlayerTableEngineTest {

    private fun table(
        vararg chips: Int,
        random: Random = Random(1),
        newDeck: (() -> Deck)? = null
    ): TableEngine = TableEngine(
        config = GameConfig(),
        players = chips.mapIndexed { i, c -> PlayerInit("P$i", c) },
        random = random,
        newDeck = newDeck ?: { Deck(random) }
    )

    private fun assertConserved(engine: TableEngine, total: Int) {
        assertEquals(total, engine.state.players.sumOf { it.chips + it.totalCommitted })
    }

    @Test
    fun fourWayAllInCreatesThreeLayerSidePots() {
        // 筹码 100/200/300/400 全部全下：400 中未被跟注的 100 退还，
        // 形成 400(4人)/300(3人)/200(2人) 三层边池；牌力 A>B>C>D
        val engine = table(100, 200, 300, 400, newDeck = {
            stackedDeck("As Ad", "Kh Kd", "Qc Qd", "7c 8c", board = "Ah Ks Qh Jc 2c")
        })
        engine.startHand()
        // dealer=0, SB=1, BB=2, 首行动=3
        engine.act(3, PlayerAction.RaiseTo(400))
        engine.act(0, PlayerAction.Call)
        engine.act(1, PlayerAction.Call)
        engine.act(2, PlayerAction.Call)
        assertEquals(Phase.HAND_OVER, engine.state.phase)
        assertEquals(5, engine.state.board.size)
        val awards = engine.state.result!!.awards
        assertEquals(3, awards.size)
        assertEquals(400, awards[0].amount)
        assertEquals(listOf(0), awards[0].winners)
        assertEquals(300, awards[1].amount)
        assertEquals(listOf(1), awards[1].winners)
        assertEquals(200, awards[2].amount)
        assertEquals(listOf(2), awards[2].winners)
        assertEquals(400, engine.state.of(0).chips)
        assertEquals(300, engine.state.of(1).chips)
        assertEquals(200, engine.state.of(2).chips)
        assertEquals(100, engine.state.of(3).chips)
        assertEquals(1000, engine.state.players.sumOf { it.chips })
    }

    @Test
    fun foldedPlayerMoneyStaysInPot() {
        val engine = table(2000, 2000, 2000, newDeck = {
            stackedDeck("2h 3d", "As Ad", "7c 8c", board = "Ah Kd Qc Jh 2c")
        })
        engine.startHand()
        engine.act(0, PlayerAction.RaiseTo(100))
        engine.act(1, PlayerAction.Call)
        engine.act(2, PlayerAction.Call)
        assertEquals(Phase.FLOP, engine.state.phase)
        assertEquals(1, engine.state.acting)
        engine.act(1, PlayerAction.Check)
        engine.act(2, PlayerAction.Check)
        engine.act(0, PlayerAction.Fold)
        engine.act(1, PlayerAction.Check)
        engine.act(2, PlayerAction.Check)
        engine.act(1, PlayerAction.Check)
        engine.act(2, PlayerAction.Check)
        assertEquals(Phase.HAND_OVER, engine.state.phase)
        val award = engine.state.result!!.awards.single()
        assertEquals(300, award.amount)
        assertEquals(listOf(1), award.winners)
        assertTrue(0 !in engine.state.result!!.revealed.keys)
        assertEquals(1900, engine.state.of(0).chips)
        assertEquals(2200, engine.state.of(1).chips)
        assertEquals(1900, engine.state.of(2).chips)
    }

    @Test
    fun splitPotOddChipGoesLeftOfDealer() {
        // 两家 1001 全下 + 第三家跟注，池 3003；前两家同花顺平手，
        // 余数 1 给 dealer(0) 左侧第一个赢家座位 1
        val engine = table(1001, 1001, 2000, newDeck = {
            stackedDeck("Qs 3h", "Qd 4h", "7c 8c", board = "As Ah Kd Kc 3c")
        })
        engine.startHand()
        engine.act(0, PlayerAction.RaiseTo(1001))
        engine.act(1, PlayerAction.Call)
        engine.act(2, PlayerAction.Call)
        assertEquals(Phase.HAND_OVER, engine.state.phase)
        val award = engine.state.result!!.awards.single()
        assertEquals(3003, award.amount)
        assertEquals(listOf(0, 1), award.winners.sorted())
        assertEquals(1501, engine.state.of(0).chips)
        assertEquals(1502, engine.state.of(1).chips)
        assertEquals(999, engine.state.of(2).chips)
    }

    @Test
    fun sixPlayerPreflopRotation() {
        val engine = table(2000, 2000, 2000, 2000, 2000, 2000)
        engine.startHand()
        assertEquals(0, engine.state.dealer)
        assertEquals(10, engine.state.of(1).streetBet)
        assertEquals(20, engine.state.of(2).streetBet)
        assertEquals(3, engine.state.acting)
        engine.act(3, PlayerAction.Call)
        assertEquals(4, engine.state.acting)
        engine.act(4, PlayerAction.Call)
        assertEquals(5, engine.state.acting)
        engine.act(5, PlayerAction.Call)
        assertEquals(0, engine.state.acting)
        engine.act(0, PlayerAction.Call)
        assertEquals(1, engine.state.acting)
        engine.act(1, PlayerAction.Call)
        assertEquals(2, engine.state.acting)
        assertTrue(engine.legalActions(2).canCheck)
        engine.act(2, PlayerAction.Check)
        assertEquals(Phase.FLOP, engine.state.phase)
        assertEquals(1, engine.state.acting)
        assertEquals(120, engine.state.pot)
    }

    @Test
    fun postflopFirstActorIsNextOfDealer() {
        val engine = table(2000, 2000, 2000, 2000)
        engine.startHand()
        engine.act(3, PlayerAction.Call)
        engine.act(0, PlayerAction.Call)
        engine.act(1, PlayerAction.Call)
        engine.act(2, PlayerAction.Check)
        assertEquals(Phase.FLOP, engine.state.phase)
        assertEquals(1, engine.state.acting)
    }

    @Test
    fun fullRaiseReopensForAllActedPlayers() {
        val engine = table(2000, 2000, 2000)
        engine.startHand()
        engine.act(0, PlayerAction.RaiseTo(40))
        engine.act(1, PlayerAction.Call)
        engine.act(2, PlayerAction.Call)
        assertEquals(Phase.FLOP, engine.state.phase)
        engine.act(1, PlayerAction.RaiseTo(40))
        assertEquals(2, engine.state.acting)
        engine.act(2, PlayerAction.Call)
        assertEquals(0, engine.state.acting)
        val legal = engine.legalActions(0)
        assertTrue(legal.canRaise)
        assertEquals(80, legal.minRaiseTo)
    }

    @Test
    fun dealerRotationSkipsEliminated() {
        val engine = table(2000, 20, 2000, newDeck = {
            stackedDeck("As Ad", "2h 3d", "7c 8c", board = "Ah Kd Qc Jh 2c")
        })
        engine.startHand()
        engine.act(0, PlayerAction.RaiseTo(2000))
        engine.act(1, PlayerAction.Call)
        engine.act(2, PlayerAction.Fold)
        assertEquals(Phase.HAND_OVER, engine.state.phase)
        assertEquals(0, engine.state.of(1).chips)
        assertEquals(2040, engine.state.of(0).chips)
        assertEquals(1980, engine.state.of(2).chips)
        engine.startHand()
        assertEquals(2, engine.state.dealer)
        assertTrue(engine.state.of(1).isFolded)
        assertTrue(engine.state.of(1).hole.isEmpty())
        // 剩余两家进入 heads-up 规则：dealer=SB 且翻前先行动
        assertEquals(2, engine.state.acting)
        assertEquals(10, engine.state.of(2).streetBet)
        assertEquals(20, engine.state.of(0).streetBet)
    }

    @Test
    fun eliminatedPlayerCannotActInNewHand() {
        val engine = table(20, 2000, 2000, newDeck = {
            stackedDeck("2h 3d", "As Ad", "7c 8c", board = "Ah Kd Qc Jh 2c")
        })
        engine.startHand()
        engine.act(0, PlayerAction.Call)
        engine.act(1, PlayerAction.Call)
        engine.act(2, PlayerAction.Check)
        repeat(3) {
            engine.act(1, PlayerAction.Check)
            engine.act(2, PlayerAction.Check)
        }
        assertEquals(Phase.HAND_OVER, engine.state.phase)
        assertEquals(0, engine.state.of(0).chips)
        engine.startHand()
        assertTrue(engine.state.of(0).isFolded)
        assertTrue(engine.state.of(0).hole.isEmpty())
        assertEquals(2, engine.state.players.count { !it.isFolded })
        assertFailsWith<IllegalStateException> { engine.act(0, PlayerAction.Call) }
    }

    @Test
    fun showdownRevealsOnlyContenders() {
        val engine = table(2000, 2000, 2000, newDeck = {
            stackedDeck("2h 3d", "As Ad", "7c 8c", board = "Ah Kd Qc Jh 2c")
        })
        engine.startHand()
        engine.act(0, PlayerAction.RaiseTo(100))
        engine.act(1, PlayerAction.Call)
        engine.act(2, PlayerAction.Fold)
        engine.act(1, PlayerAction.Check)
        engine.act(0, PlayerAction.Check)
        engine.act(1, PlayerAction.Check)
        engine.act(0, PlayerAction.Check)
        engine.act(1, PlayerAction.Check)
        engine.act(0, PlayerAction.Check)
        assertEquals(setOf(0, 1), engine.state.result!!.revealed.keys)
    }

    @Test
    fun seatCountValidated() {
        assertFailsWith<IllegalArgumentException> {
            TableEngine(players = listOf(PlayerInit("solo")))
        }
        assertFailsWith<IllegalArgumentException> {
            TableEngine(players = (1..7).map { PlayerInit("P$it") })
        }
    }

    private fun randomAction(legal: LegalActions, random: Random): PlayerAction {
        if (legal.canFold && random.nextDouble() < 0.10) return PlayerAction.Fold
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
    fun chipConservationStressMultiPlayer() {
        for (n in 3..6) {
            val random = Random(100 + n)
            val engine = TableEngine(
                players = (0 until n).map { PlayerInit("P$it") },
                random = random
            )
            val total = 2000 * n
            engine.startMatch()
            repeat(150) {
                if (engine.state.phase == Phase.MATCH_OVER) engine.startMatch()
                engine.startHand()
                var guard = 0
                while (engine.state.phase.isBetting()) {
                    check(++guard < 400) { "下注轮死循环 n=$n" }
                    val seat = engine.state.acting!!
                    engine.act(seat, randomAction(engine.legalActions(seat), random))
                    assertConserved(engine, total)
                }
                assertEquals(total, engine.state.players.sumOf { it.chips })
                assertEquals(0, engine.state.pot)
            }
        }
    }
}