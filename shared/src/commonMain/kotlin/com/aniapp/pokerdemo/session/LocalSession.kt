package com.aniapp.pokerdemo.session

import com.aniapp.pokerdemo.engine.Deck
import com.aniapp.pokerdemo.engine.GameEvent
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.PlayerInit
import com.aniapp.pokerdemo.engine.TableEngine
import com.aniapp.pokerdemo.engine.ai.AiStrategy
import com.aniapp.pokerdemo.engine.ai.AiView
import com.aniapp.pokerdemo.engine.ai.RuleBasedAi
import com.aniapp.pokerdemo.net.SeatInfo
import com.aniapp.pokerdemo.net.TableView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class LocalSession(
    private val scope: CoroutineScope,
    aiCount: Int = 1,
    private val engine: TableEngine = defaultEngine(aiCount),
    private val ai: AiStrategy = RuleBasedAi(),
    private val aiRandom: Random = Random.Default
) : GameSession {

    private val mySeat = 0

    private val _uiState = MutableStateFlow(
        GameUiState(
            table = engine.toTableView(mySeat),
            smallBlind = engine.config.smallBlind,
            bigBlind = engine.config.bigBlind
        )
    )
    override val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var aiJob: Job? = null

    init {
        newMatch()
    }

    override fun act(action: PlayerAction) {
        if (!_uiState.value.isPlayerTurn) return
        publish(engine.act(mySeat, action))
    }

    override fun nextHand() {
        if (engine.state.phase != Phase.HAND_OVER) return
        publish(engine.startHand())
    }

    override fun newMatch() {
        aiJob?.cancel()
        aiJob = null
        engine.startMatch()
        publish(engine.startHand())
    }

    override fun dispose() {
        aiJob?.cancel()
        aiJob = null
    }

    private fun publish(events: List<GameEvent>) {
        _uiState.value = GameUiState(
            table = engine.toTableView(mySeat),
            lastEvents = events,
            smallBlind = engine.config.smallBlind,
            bigBlind = engine.config.bigBlind
        )
        maybeStartAi()
    }

    private fun maybeStartAi() {
        val g = engine.state
        val acting = g.acting ?: return
        if (acting == mySeat || !g.phase.isBetting()) return
        if (aiJob?.isActive == true) return
        aiJob = scope.launch {
            while (true) {
                val cur = engine.state
                val s = cur.acting ?: break
                if (s == mySeat || !cur.phase.isBetting()) break
                _uiState.update { it.copy(aiThinking = true) }
                delay(aiRandom.nextLong(700, 1401))
                val legal = engine.legalActions(s)
                val action = ai.decide(aiViewOf(s, legal.callAmount), legal, aiRandom)
                publish(engine.act(s, action))
            }
            _uiState.update { it.copy(aiThinking = false) }
        }
    }

    private fun aiViewOf(seat: Int, toCall: Int): AiView {
        val g = engine.state
        val me = g.of(seat)
        return AiView(
            hole = me.hole,
            board = g.board,
            phase = g.phase,
            pot = g.pot,
            toCall = toCall,
            streetBet = me.streetBet,
            chips = me.chips,
            bigBlind = engine.config.bigBlind,
            activeOpponents = g.players.count { !it.isFolded && it.id != seat }
        )
    }

    companion object {
        fun defaultEngine(
            aiCount: Int,
            newDeck: (() -> Deck)? = null,
            chips: List<Int>? = null
        ): TableEngine {
            val names = listOf(PlayerInit("你")) + (1..aiCount).map {
                PlayerInit(if (aiCount == 1) "电脑" else "电脑$it")
            }
            val players = names.mapIndexed { i, p -> PlayerInit(p.name, chips?.getOrNull(i)) }
            return if (newDeck != null) TableEngine(players = players, newDeck = newDeck)
            else TableEngine(players = players)
        }
    }
}

fun TableEngine.toTableView(mySeat: Int): TableView {
    val s = state
    val revealed = if (s.showdown) {
        s.players.filter { !it.isFolded }.associate { it.id to it.hole }
    } else emptyMap()
    return TableView(
        seats = s.players.map { p ->
            SeatInfo(
                seat = p.id,
                name = p.name,
                chips = p.chips,
                streetBet = p.streetBet,
                totalCommitted = p.totalCommitted,
                folded = p.isFolded,
                allIn = p.isAllIn,
                connected = true,
                isAi = p.id != mySeat
            )
        },
        board = s.board,
        dealer = s.dealer,
        acting = s.acting,
        phase = s.phase,
        handNumber = s.handNumber,
        currentBet = s.currentBet,
        minRaiseTo = s.minRaiseTo,
        pot = s.pot,
        mySeat = mySeat,
        myHole = s.of(mySeat).hole,
        legal = legalActions(mySeat),
        result = s.result,
        revealedHoles = revealed
    )
}