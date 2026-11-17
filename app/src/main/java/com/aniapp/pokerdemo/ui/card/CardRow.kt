package com.aniapp.pokerdemo.ui.card

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aniapp.pokerdemo.engine.Card
import kotlinx.coroutines.delay

@Composable
fun HandRow(
    cards: List<Card>,
    faceUp: Boolean,
    size: CardSize,
    handNumber: Int,
    modifier: Modifier = Modifier
) {
    val visible = remember(handNumber) { mutableIntStateOf(0) }
    LaunchedEffect(handNumber, cards.size) {
        if (visible.intValue > cards.size) visible.intValue = 0
        while (visible.intValue < cards.size) {
            delay(120)
            visible.intValue++
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) { i ->
            val card = cards.getOrNull(i)
            CardSlot(visible = card != null && i < visible.intValue, size = size) {
                if (card != null) PlayingCard(card, faceUp, size)
            }
        }
    }
}

@Composable
fun BoardRow(
    board: List<Card>,
    size: CardSize,
    handNumber: Int,
    modifier: Modifier = Modifier
) {
    val visible = remember(handNumber) { mutableIntStateOf(0) }
    LaunchedEffect(handNumber, board.size) {
        if (visible.intValue > board.size) visible.intValue = 0
        while (visible.intValue < board.size) {
            delay(120)
            visible.intValue++
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { i ->
            val card = board.getOrNull(i)
            CardSlot(
                visible = card != null && i < visible.intValue,
                size = size,
                placeholder = { EmptySlot() }
            ) {
                if (card != null) PlayingCard(card, true, size)
            }
        }
    }
}

@Composable
private fun CardSlot(
    visible: Boolean,
    size: CardSize,
    placeholder: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Box(Modifier.size(size.width, size.height)) {
        if (!visible) placeholder()
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(250)) + slideInHorizontally(tween(250)) { -it / 2 },
            exit = fadeOut(tween(150))
        ) {
            content()
        }
    }
}

@Composable
private fun EmptySlot() {
    Box(
        Modifier
            .fillMaxSize()
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
    )
}