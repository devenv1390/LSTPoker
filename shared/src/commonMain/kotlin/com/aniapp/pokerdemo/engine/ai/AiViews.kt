package com.aniapp.pokerdemo.engine.ai

import com.aniapp.pokerdemo.engine.GameState
import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.PlayerAction

fun aiViewOf(state: GameState, seat: Int, toCall: Int, bigBlind: Int): AiView {
    val me = state.of(seat)
    return AiView(
        hole = me.hole,
        board = state.board,
        phase = state.phase,
        pot = state.pot,
        toCall = toCall,
        streetBet = me.streetBet,
        chips = me.chips,
        bigBlind = bigBlind,
        activeOpponents = state.players.count { !it.isFolded && it.id != seat }
    )
}

/** 把策略输出收敛到当前合法动作空间，保证引擎可执行 */
fun sanitize(action: PlayerAction, legal: LegalActions): PlayerAction = when (action) {
    PlayerAction.Fold -> when {
        legal.canFold -> action
        legal.canCheck -> PlayerAction.Check
        legal.canCall -> PlayerAction.Call
        else -> action
    }
    PlayerAction.Check ->
        if (legal.canCheck) action else if (legal.canCall) PlayerAction.Call else PlayerAction.Fold
    PlayerAction.Call ->
        if (legal.canCall) action else if (legal.canCheck) PlayerAction.Check else PlayerAction.Fold
    is PlayerAction.RaiseTo -> when {
        legal.canRaise -> PlayerAction.RaiseTo(action.amount.coerceIn(legal.minRaiseTo!!, legal.maxRaiseTo!!))
        legal.canCall -> PlayerAction.Call
        legal.canCheck -> PlayerAction.Check
        else -> PlayerAction.Fold
    }
}
