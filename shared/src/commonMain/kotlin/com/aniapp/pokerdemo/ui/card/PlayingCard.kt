package com.aniapp.pokerdemo.ui.card

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.Suit
import com.aniapp.pokerdemo.ui.theme.CardBlack
import com.aniapp.pokerdemo.ui.theme.CardRed
import com.aniapp.pokerdemo.ui.theme.CardWhite

data class CardSize(val width: Dp) {
    val height: Dp get() = width * 1.4f
}

@Composable
fun PlayingCard(card: Card?, faceUp: Boolean, size: CardSize, modifier: Modifier = Modifier) {
    val showFace = faceUp && card != null
    val rotation by animateFloatAsState(
        targetValue = if (showFace) 0f else 180f,
        animationSpec = tween(400),
        label = "cardFlip"
    )
    Box(
        modifier
            .size(size.width, size.height)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12 * density
            }
    ) {
        if (rotation <= 90f) {
            if (card != null) CardFace(card, size) else CardBack(size)
        } else {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                CardBack(size)
            }
        }
    }
}

@Composable
private fun CardFace(card: Card, size: CardSize) {
    val rankStr = when (card.rank) {
        14 -> "A"; 13 -> "K"; 12 -> "Q"; 11 -> "J"
        else -> card.rank.toString()
    }
    val symbol = when (card.suit) {
        Suit.SPADES -> "♠"; Suit.HEARTS -> "♥"; Suit.DIAMONDS -> "♦"; Suit.CLUBS -> "♣"
    }
    val color = when (card.suit) {
        Suit.SPADES, Suit.CLUBS -> CardBlack
        else -> CardRed
    }
    val cornerFont = (size.width.value * 0.22f).sp
    val cornerSuitFont = (size.width.value * 0.20f).sp
    Box(
        Modifier
            .fillMaxSize()
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .background(CardWhite, RoundedCornerShape(8.dp))
            .padding(size.width * 0.08f)
    ) {
        Column(Modifier.align(Alignment.TopStart)) {
            Text(rankStr, fontSize = cornerFont, lineHeight = cornerFont, color = color, fontWeight = FontWeight.Bold)
            Text(symbol, fontSize = cornerSuitFont, lineHeight = cornerSuitFont, color = color)
        }
        Text(
            symbol,
            fontSize = (size.width.value * 0.5f).sp,
            lineHeight = (size.width.value * 0.5f).sp,
            color = color,
            modifier = Modifier.align(Alignment.Center)
        )
        Column(Modifier.align(Alignment.BottomEnd).rotate(180f)) {
            Text(rankStr, fontSize = cornerFont, lineHeight = cornerFont, color = color, fontWeight = FontWeight.Bold)
            Text(symbol, fontSize = cornerSuitFont, lineHeight = cornerSuitFont, color = color)
        }
    }
}

@Composable
private fun CardBack(size: CardSize) {
    Box(
        Modifier
            .fillMaxSize()
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF2A4D8F), Color(0xFF152A52))),
                RoundedCornerShape(8.dp)
            )
            .border(1.dp, Color(0xFF8FA8D8), RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp))) {
            val step = size.width.toPx() / 4f
            val lineColor = Color(0xFF4A6FA5)
            var x = -this.size.height
            while (x < this.size.width + this.size.height) {
                drawLine(lineColor, Offset(x, 0f), Offset(x + this.size.height, this.size.height), strokeWidth = 1.5f)
                drawLine(lineColor, Offset(x, this.size.height), Offset(x + this.size.height, 0f), strokeWidth = 1.5f)
                x += step
            }
        }
    }
}