package com.aniapp.pokerdemo.engine

import kotlin.random.Random

data class GameConfig(
    val startingChips: Int = 2000,
    val smallBlind: Int = 10,
    val bigBlind: Int = 20
)

data class PlayerInit(val name: String, val chips: Int? = null)

class TableEngine(
    val config: GameConfig = GameConfig(),
    players: List<PlayerInit>,
    private val random: Random = Random.Default,
    private val newDeck: () -> Deck = { Deck(random) }
) {
    init {
        require(players.size in 2..6) { "座位数必须为 2-6: ${players.size}" }
    }

    private val initialChips = players.map { it.chips ?: config.startingChips }

    var state: GameState = GameState(
        players = players.mapIndexed { i, p -> PlayerState(i, p.name, initialChips[i]) }
    )
        private set

    private var deck: Deck = newDeck()
    private var lastRaiseSize = config.bigBlind

    fun startMatch() {
        deck = newDeck()
        lastRaiseSize = config.bigBlind
        state = GameState(
            players = state.players.map { PlayerState(it.id, it.name, initialChips[it.id]) }
        )
    }

    fun startHand(): List<GameEvent> {
        check(state.phase == Phase.IDLE || state.phase == Phase.HAND_OVER) {
            "当前阶段不能开始新一手: ${state.phase}"
        }
        check(state.players.count { it.chips > 0 } >= 2) { "剩余筹码不足两人，对局已结束" }
        val events = mutableListOf<GameEvent>()
        val dealer = if (state.phase == Phase.IDLE) state.dealer
        else nextSeat(state.dealer) { it.chips > 0 }
        deck = newDeck()
        lastRaiseSize = config.bigBlind
        state = state.copy(
            handNumber = state.handNumber + 1,
            dealer = dealer,
            phase = Phase.PREFLOP,
            board = emptyList(),
            players = state.players.map { p ->
                if (p.chips > 0) PlayerState(p.id, p.name, p.chips, hole = List(2) { deck.draw() })
                else p.copy(hole = emptyList(), isFolded = true)
            },
            acting = null,
            currentBet = 0,
            minRaiseTo = 0,
            result = null,
            showdown = false
        )
        events += GameEvent.HoleCardsDealt
        val headsUp = state.players.count { it.chips > 0 } == 2
        val sb = if (headsUp) dealer else nextSeat(dealer) { it.chips > 0 }
        val bb = nextSeat(sb) { it.chips > 0 }
        postBlind(sb, config.smallBlind, events)
        postBlind(bb, config.bigBlind, events)
        val topBet = state.players.maxOf { it.streetBet }
        state = state.copy(currentBet = topBet, minRaiseTo = topBet + config.bigBlind)
        val first = if (headsUp) {
            if (state.of(dealer).isAllIn) bb else dealer
        } else {
            nextSeat(bb) { !it.isFolded && it.chips > 0 }
        }
        state = state.copy(acting = first)
        if (roundComplete()) {
            state = state.copy(acting = null)
            advanceStreet(events)
        }
        return events
    }

    fun legalActions(seat: Int): LegalActions {
        if (state.acting != seat || !state.phase.isBetting()) return LegalActions()
        val me = state.of(seat)
        val toCall = (state.currentBet - me.streetBet).coerceAtLeast(0)
        val maxTo = me.streetBet + me.chips
        val canRaise = maxTo > state.currentBet && (!me.hasActedThisRound || me.raiseReopened)
        return LegalActions(
            canFold = true,
            canCheck = toCall == 0,
            callAmount = minOf(toCall, me.chips),
            minRaiseTo = if (canRaise) minOf(state.minRaiseTo, maxTo) else null,
            maxRaiseTo = if (canRaise) maxTo else null
        )
    }

    fun act(seat: Int, action: PlayerAction): List<GameEvent> {
        check(state.phase.isBetting()) { "当前阶段不可行动: ${state.phase}" }
        check(state.acting == seat) { "未轮到座位 $seat 行动" }
        val legal = legalActions(seat)
        val events = mutableListOf<GameEvent>()
        when (action) {
            PlayerAction.Fold -> {
                updateSeat(seat) { it.copy(isFolded = true, hasActedThisRound = true) }
                events += GameEvent.ActionTaken(seat, action)
                val contenders = state.players.filter { !it.isFolded }
                if (contenders.size == 1) {
                    state = state.copy(acting = null)
                    settle(EndReason.FOLD, events)
                    return events
                }
            }
            PlayerAction.Check -> {
                check(legal.canCheck) { "面对下注不能过牌" }
                updateSeat(seat) { it.copy(hasActedThisRound = true) }
            }
            PlayerAction.Call -> {
                check(legal.canCall) { "无需跟注" }
                pay(seat, legal.callAmount)
                updateSeat(seat) { it.copy(hasActedThisRound = true) }
            }
            is PlayerAction.RaiseTo -> {
                check(legal.canRaise) { "当前不能加注" }
                check(action.amount in legal.minRaiseTo!!..legal.maxRaiseTo!!) {
                    "加注额非法: ${action.amount}"
                }
                val prevBet = state.currentBet
                val fullRaise = action.amount >= state.minRaiseTo
                pay(seat, action.amount - state.of(seat).streetBet)
                if (fullRaise) lastRaiseSize = action.amount - prevBet
                state = state.copy(currentBet = action.amount, minRaiseTo = action.amount + lastRaiseSize)
                updateSeat(seat) { it.copy(hasActedThisRound = true, raiseReopened = false) }
                if (fullRaise) {
                    state = state.copy(players = state.players.map {
                        if (it.id != seat && !it.isFolded && !it.isAllIn) it.copy(raiseReopened = true) else it
                    })
                }
            }
        }
        events += GameEvent.ActionTaken(seat, action)
        if (roundComplete()) {
            state = state.copy(acting = null)
            advanceStreet(events)
        } else {
            state = state.copy(acting = nextSeat(seat) { !it.isFolded && it.chips > 0 })
        }
        return events
    }

    private fun nextSeat(from: Int, predicate: (PlayerState) -> Boolean): Int {
        val n = state.players.size
        for (step in 1..n) {
            val i = (from + step) % n
            if (predicate(state.players[i])) return i
        }
        return from
    }

    private fun postBlind(seat: Int, amount: Int, events: MutableList<GameEvent>) {
        val paid = minOf(amount, state.of(seat).chips)
        updateSeat(seat) {
            it.copy(
                chips = it.chips - paid,
                streetBet = paid,
                totalCommitted = paid,
                isAllIn = it.chips - paid == 0
            )
        }
        events += GameEvent.BlindsPosted(seat, paid)
    }

    private fun pay(seat: Int, amount: Int) {
        updateSeat(seat) {
            val paid = minOf(amount, it.chips)
            it.copy(
                chips = it.chips - paid,
                streetBet = it.streetBet + paid,
                totalCommitted = it.totalCommitted + paid,
                isAllIn = it.chips - paid == 0
            )
        }
    }

    private fun roundComplete(): Boolean {
        val contenders = state.players.filter { !it.isFolded }
        if (contenders.size <= 1) return true
        val actionable = contenders.filter { it.chips > 0 }
        if (actionable.isEmpty()) return true
        if (actionable.size == 1) {
            val lone = actionable.single()
            return lone.hasActedThisRound && lone.streetBet >= state.currentBet
        }
        return actionable.all { it.hasActedThisRound && it.streetBet == state.currentBet }
    }

    private fun advanceStreet(events: MutableList<GameEvent>) {
        while (true) {
            state = state.copy(
                players = state.players.map {
                    it.copy(streetBet = 0, hasActedThisRound = false, raiseReopened = false)
                },
                currentBet = 0,
                minRaiseTo = config.bigBlind
            )
            lastRaiseSize = config.bigBlind
            val next = when (state.phase) {
                Phase.PREFLOP -> Phase.FLOP
                Phase.FLOP -> Phase.TURN
                Phase.TURN -> Phase.RIVER
                Phase.RIVER -> {
                    showdown(events)
                    return
                }
                else -> return
            }
            deck.burn()
            val count = if (next == Phase.FLOP) 3 else 1
            val newCards = List(count) { deck.draw() }
            val runout = state.players.count { !it.isFolded && it.chips > 0 } <= 1
            state = state.copy(
                phase = next,
                board = state.board + newCards,
                acting = if (runout) null else nextSeat(state.dealer) { !it.isFolded && it.chips > 0 }
            )
            events += GameEvent.StreetDealt(next, newCards, runout)
            if (!runout) return
        }
    }

    private fun showdown(events: MutableList<GameEvent>) {
        state = state.copy(phase = Phase.SHOWDOWN, showdown = true, acting = null)
        events += GameEvent.ShowdownRevealed
        settle(EndReason.SHOWDOWN, events)
    }

    private fun settle(reason: EndReason, events: MutableList<GameEvent>) {
        val committed = state.players.map { it.totalCommitted }
        val maxC = committed.max()
        val maxHolders = committed.indices.filter { committed[it] == maxC }
        if (maxHolders.size == 1 && maxC > 0) {
            val second = committed.filter { it < maxC }.maxOrNull() ?: 0
            val diff = maxC - second
            if (diff > 0) {
                val s = maxHolders[0]
                updateSeat(s) { it.copy(chips = it.chips + diff, totalCommitted = it.totalCommitted - diff) }
                events += GameEvent.UncalledReturned(s, diff)
            }
        }

        val awards = mutableListOf<PotAward>()
        val revealed = mutableMapOf<Int, HandValue>()
        if (reason == EndReason.FOLD) {
            val winner = state.players.single { !it.isFolded }.id
            val pot = state.pot
            updateSeat(winner) { it.copy(chips = it.chips + pot) }
            awards += PotAward(pot, listOf(winner), null)
            events += GameEvent.PotAwarded(0, listOf(winner), pot)
        } else {
            val contenders = state.players.filter { !it.isFolded }
            val values = contenders.associate { it.id to HandEvaluator.evaluate(it.hole + state.board) }
            revealed.putAll(values)
            val levels = state.players.map { it.totalCommitted }.filter { it > 0 }.distinct().sorted()
            val amounts = mutableListOf<Int>()
            val eligibles = mutableListOf<List<Int>>()
            var prev = 0
            for (level in levels) {
                val contributors = state.players.filter { it.totalCommitted >= level }
                amounts += (level - prev) * contributors.size
                eligibles += contributors.filter { !it.isFolded }.map { it.id }
                prev = level
            }
            for (i in amounts.indices.reversed()) {
                if (eligibles[i].isEmpty() && i > 0) {
                    amounts[i - 1] += amounts[i]
                    amounts[i] = 0
                }
            }
            var potIndex = 0
            for (i in amounts.indices) {
                val amount = amounts[i]
                if (amount == 0) continue
                val eligible = eligibles[i]
                val best = eligible.maxOf { values.getValue(it).score }
                val winners = eligible.filter { values.getValue(it).score == best }
                val share = amount / winners.size
                var remainder = amount % winners.size
                for (w in orderFromDealer(winners)) {
                    val extra = if (remainder > 0) 1 else 0
                    if (extra > 0) remainder--
                    updateSeat(w) { it.copy(chips = it.chips + share + extra) }
                }
                awards += PotAward(amount, winners, eligible.map { values.getValue(it) }.maxByOrNull { it.score })
                events += GameEvent.PotAwarded(potIndex++, winners, amount)
            }
        }

        val eliminated = state.players.filter { it.chips == 0 && it.totalCommitted > 0 }.map { it.id }
        state = state.copy(players = state.players.map { it.copy(streetBet = 0, totalCommitted = 0) })
        eliminated.forEach { events += GameEvent.PlayerEliminated(it) }
        val result = HandResult(awards, revealed, reason)
        val alive = state.players.filter { it.chips > 0 }
        val matchOver = alive.size <= 1
        state = state.copy(phase = if (matchOver) Phase.MATCH_OVER else Phase.HAND_OVER, result = result)
        events += GameEvent.HandFinished(result)
        if (matchOver) {
            events += GameEvent.MatchFinished(alive.single().id)
        }
    }

    private fun orderFromDealer(seats: List<Int>): List<Int> {
        val n = state.players.size
        return seats.sortedBy { (it - state.dealer - 1 + n) % n }
    }

    private fun updateSeat(seat: Int, f: (PlayerState) -> PlayerState) {
        state = state.copy(players = state.players.map { if (it.id == seat) f(it) else it })
    }
}