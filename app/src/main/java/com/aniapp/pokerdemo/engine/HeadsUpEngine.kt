package com.aniapp.pokerdemo.engine

import kotlin.math.abs
import kotlin.random.Random

data class GameConfig(
    val startingChips: Int = 2000,
    val smallBlind: Int = 10,
    val bigBlind: Int = 20
)

class HeadsUpEngine(
    val config: GameConfig = GameConfig(),
    private val random: Random = Random.Default,
    private val newDeck: () -> Deck = { Deck(random) }
) {
    var state: GameState = GameState(
        player = PlayerState(config.startingChips),
        ai = PlayerState(config.startingChips)
    )
        private set

    private var deck: Deck = newDeck()
    private var lastRaiseSize = config.bigBlind

    fun startMatch() {
        deck = newDeck()
        lastRaiseSize = config.bigBlind
        state = GameState(
            player = PlayerState(config.startingChips),
            ai = PlayerState(config.startingChips)
        )
    }

    fun startHand(): List<GameEvent> {
        check(state.phase == Phase.IDLE || state.phase == Phase.HAND_OVER) {
            "当前阶段不能开始新一手: ${state.phase}"
        }
        val events = mutableListOf<GameEvent>()
        val dealer = if (state.phase == Phase.IDLE) state.dealer else state.dealer.other()
        deck = newDeck()
        lastRaiseSize = config.bigBlind
        val playerHole = List(2) { deck.draw() }
        val aiHole = List(2) { deck.draw() }
        state = state.copy(
            handNumber = state.handNumber + 1,
            dealer = dealer,
            phase = Phase.PREFLOP,
            board = emptyList(),
            player = PlayerState(state.player.chips, hole = playerHole),
            ai = PlayerState(state.ai.chips, hole = aiHole),
            acting = null,
            currentBet = 0,
            minRaiseTo = 0,
            result = null,
            aiRevealed = false
        )
        events += GameEvent.HoleCardsDealt
        postBlind(dealer, config.smallBlind, events)
        postBlind(dealer.other(), config.bigBlind, events)
        val bb = maxOf(state.player.streetBet, state.ai.streetBet)
        state = state.copy(currentBet = bb, minRaiseTo = bb + config.bigBlind)
        val first = if (state.of(dealer).isAllIn) dealer.other() else dealer
        state = state.copy(acting = first)
        if (roundComplete()) {
            state = state.copy(acting = null)
            advanceStreet(events)
        }
        return events
    }

    fun legalActions(seat: Seat): LegalActions {
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

    fun act(seat: Seat, action: PlayerAction): List<GameEvent> {
        check(state.phase.isBetting()) { "当前阶段不可行动: ${state.phase}" }
        check(state.acting == seat) { "未轮到 $seat 行动" }
        val legal = legalActions(seat)
        val events = mutableListOf<GameEvent>()
        when (action) {
            PlayerAction.Fold -> {
                updateSeat(seat) { it.copy(isFolded = true, hasActedThisRound = true) }
                events += GameEvent.ActionTaken(seat, action)
                state = state.copy(acting = null)
                settle(seat.other(), EndReason.FOLD, null, null, events)
                return events
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
                if (fullRaise) updateSeat(seat.other()) { it.copy(raiseReopened = true) }
            }
        }
        events += GameEvent.ActionTaken(seat, action)
        if (roundComplete()) {
            state = state.copy(acting = null)
            advanceStreet(events)
        } else {
            state = state.copy(acting = seat.other())
        }
        return events
    }

    private fun postBlind(seat: Seat, amount: Int, events: MutableList<GameEvent>) {
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

    private fun pay(seat: Seat, amount: Int) {
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
        val p = state.player
        val a = state.ai
        if (p.isFolded || a.isFolded) return true
        if (p.streetBet == a.streetBet) {
            return (p.hasActedThisRound && a.hasActedThisRound) || p.isAllIn || a.isAllIn
        }
        if (p.isAllIn && p.streetBet < a.streetBet) return a.hasActedThisRound || a.isAllIn
        if (a.isAllIn && a.streetBet < p.streetBet) return p.hasActedThisRound || p.isAllIn
        return false
    }

    private fun advanceStreet(events: MutableList<GameEvent>) {
        while (true) {
            state = state.copy(
                player = state.player.copy(streetBet = 0, hasActedThisRound = false, raiseReopened = false),
                ai = state.ai.copy(streetBet = 0, hasActedThisRound = false, raiseReopened = false),
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
            val runout = state.player.isAllIn || state.ai.isAllIn
            state = state.copy(
                phase = next,
                board = state.board + newCards,
                acting = if (runout) null else state.dealer.other()
            )
            events += GameEvent.StreetDealt(next, newCards, runout)
            if (!runout) return
        }
    }

    private fun showdown(events: MutableList<GameEvent>) {
        state = state.copy(phase = Phase.SHOWDOWN, aiRevealed = true, acting = null)
        events += GameEvent.ShowdownRevealed
        val pv = HandEvaluator.evaluate(state.player.hole + state.board)
        val av = HandEvaluator.evaluate(state.ai.hole + state.board)
        val winner = when {
            pv > av -> Seat.PLAYER
            av > pv -> Seat.AI
            else -> null
        }
        settle(winner, EndReason.SHOWDOWN, pv, av, events)
    }

    private fun settle(
        winner: Seat?,
        reason: EndReason,
        pv: HandValue?,
        av: HandValue?,
        events: MutableList<GameEvent>
    ) {
        val pc = state.player.totalCommitted
        val ac = state.ai.totalCommitted
        if (pc != ac) {
            val side = if (pc > ac) Seat.PLAYER else Seat.AI
            val diff = abs(pc - ac)
            updateSeat(side) { it.copy(chips = it.chips + diff, totalCommitted = it.totalCommitted - diff) }
            events += GameEvent.UncalledReturned(side, diff)
        }
        val pot = state.pot
        if (winner == null) {
            val half = pot / 2
            val oddTo = state.dealer.other()
            updateSeat(Seat.PLAYER) { it.copy(chips = it.chips + half + if (oddTo == Seat.PLAYER) pot % 2 else 0) }
            updateSeat(Seat.AI) { it.copy(chips = it.chips + half + if (oddTo == Seat.AI) pot % 2 else 0) }
        } else {
            updateSeat(winner) { it.copy(chips = it.chips + pot) }
        }
        events += GameEvent.PotAwarded(winner, pot)
        state = state.copy(
            player = state.player.copy(streetBet = 0, totalCommitted = 0),
            ai = state.ai.copy(streetBet = 0, totalCommitted = 0)
        )
        val result = HandResult(winner, pot, pv, av, reason)
        val matchOver = state.player.chips == 0 || state.ai.chips == 0
        state = state.copy(phase = if (matchOver) Phase.MATCH_OVER else Phase.HAND_OVER, result = result)
        events += GameEvent.HandFinished(result)
        if (matchOver) {
            events += GameEvent.MatchFinished(if (state.player.chips == 0) Seat.AI else Seat.PLAYER)
        }
    }

    private fun updateSeat(seat: Seat, f: (PlayerState) -> PlayerState) {
        state = if (seat == Seat.PLAYER) state.copy(player = f(state.player)) else state.copy(ai = f(state.ai))
    }
}