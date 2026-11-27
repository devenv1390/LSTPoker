package com.aniapp.pokerdemo.engine

import kotlinx.serialization.Serializable

@Serializable
sealed interface PlayerAction {
    @Serializable
    data object Fold : PlayerAction
    @Serializable
    data object Check : PlayerAction
    @Serializable
    data object Call : PlayerAction
    @Serializable
    data class RaiseTo(val amount: Int) : PlayerAction
}

@Serializable
data class LegalActions(
    val canFold: Boolean = false,
    val canCheck: Boolean = false,
    val callAmount: Int = 0,
    val minRaiseTo: Int? = null,
    val maxRaiseTo: Int? = null
) {
    val canCall: Boolean get() = callAmount > 0
    val canRaise: Boolean get() = minRaiseTo != null && maxRaiseTo != null

    fun accepts(action: PlayerAction): Boolean = when (action) {
        PlayerAction.Fold -> canFold
        PlayerAction.Check -> canCheck
        PlayerAction.Call -> canCall
        is PlayerAction.RaiseTo -> canRaise && action.amount in minRaiseTo!!..maxRaiseTo!!
    }
}