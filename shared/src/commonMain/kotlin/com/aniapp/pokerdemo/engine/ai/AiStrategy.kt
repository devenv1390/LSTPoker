package com.aniapp.pokerdemo.engine.ai

import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import kotlin.random.Random

data class AiView(
    val hole: List<Card>,
    val board: List<Card>,
    val phase: Phase,
    val pot: Int,
    val toCall: Int,
    val streetBet: Int,
    val chips: Int,
    val bigBlind: Int,
    val activeOpponents: Int = 1
)

interface AiStrategy {
    fun decide(view: AiView, legal: LegalActions, random: Random): PlayerAction
}