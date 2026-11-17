package com.aniapp.pokerdemo.vm

import com.aniapp.pokerdemo.engine.GameEvent
import com.aniapp.pokerdemo.engine.GameState
import com.aniapp.pokerdemo.engine.LegalActions

data class GameUiState(
    val game: GameState,
    val legal: LegalActions,
    val isPlayerTurn: Boolean,
    val aiThinking: Boolean,
    val lastEvents: List<GameEvent>,
    val smallBlind: Int,
    val bigBlind: Int
)