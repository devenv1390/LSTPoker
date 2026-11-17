package com.aniapp.pokerdemo.engine

sealed interface PlayerAction {
    data object Fold : PlayerAction
    data object Check : PlayerAction
    data object Call : PlayerAction
    data class RaiseTo(val amount: Int) : PlayerAction
}

data class LegalActions(
    val canFold: Boolean = false,
    val canCheck: Boolean = false,
    val callAmount: Int = 0,
    val minRaiseTo: Int? = null,
    val maxRaiseTo: Int? = null
) {
    val canCall: Boolean get() = callAmount > 0
    val canRaise: Boolean get() = minRaiseTo != null && maxRaiseTo != null
}