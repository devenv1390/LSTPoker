package com.aniapp.pokerdemo.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniapp.pokerdemo.engine.GameEvent
import com.aniapp.pokerdemo.engine.GameState
import com.aniapp.pokerdemo.engine.HeadsUpEngine
import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.Seat
import com.aniapp.pokerdemo.engine.ai.AiStrategy
import com.aniapp.pokerdemo.engine.ai.AiView
import com.aniapp.pokerdemo.engine.ai.RuleBasedAi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameViewModel(
    private val engine: HeadsUpEngine = HeadsUpEngine(),
    private val ai: AiStrategy = RuleBasedAi(),
    private val aiRandom: Random = Random.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        GameUiState(
            game = engine.state,
            legal = LegalActions(),
            isPlayerTurn = false,
            aiThinking = false,
            lastEvents = emptyList(),
            smallBlind = engine.config.smallBlind,
            bigBlind = engine.config.bigBlind
        )
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var aiJob: Job? = null

    init {
        onNewMatch()
    }

    fun onPlayerAction(action: PlayerAction) {
        if (!_uiState.value.isPlayerTurn) return
        publish(engine.act(Seat.PLAYER, action))
    }

    fun onNextHand() {
        if (engine.state.phase != Phase.HAND_OVER) return
        publish(engine.startHand())
    }

    fun onNewMatch() {
        aiJob?.cancel()
        aiJob = null
        engine.startMatch()
        publish(engine.startHand())
    }

    private fun publish(events: List<GameEvent>) {
        val g = engine.state
        _uiState.value = GameUiState(
            game = g,
            legal = engine.legalActions(Seat.PLAYER),
            isPlayerTurn = g.acting == Seat.PLAYER && g.phase.isBetting(),
            aiThinking = false,
            lastEvents = events,
            smallBlind = engine.config.smallBlind,
            bigBlind = engine.config.bigBlind
        )
        maybeStartAi()
    }

    private fun maybeStartAi() {
        val g = engine.state
        if (g.acting != Seat.AI || !g.phase.isBetting()) return
        if (aiJob?.isActive == true) return
        aiJob = viewModelScope.launch {
            while (true) {
                val cur = engine.state
                if (cur.acting != Seat.AI || !cur.phase.isBetting()) break
                _uiState.update { it.copy(aiThinking = true) }
                delay(aiRandom.nextLong(700, 1401))
                val legal = engine.legalActions(Seat.AI)
                val action = ai.decide(aiViewOf(engine.state, legal.callAmount), legal, aiRandom)
                publish(engine.act(Seat.AI, action))
            }
            _uiState.update { it.copy(aiThinking = false) }
        }
    }

    private fun aiViewOf(g: GameState, toCall: Int): AiView {
        val me = g.of(Seat.AI)
        return AiView(
            hole = me.hole,
            board = g.board,
            phase = g.phase,
            pot = g.pot,
            toCall = toCall,
            streetBet = me.streetBet,
            chips = me.chips,
            bigBlind = engine.config.bigBlind
        )
    }
}