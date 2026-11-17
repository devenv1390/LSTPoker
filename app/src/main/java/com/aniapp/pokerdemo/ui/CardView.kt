package com.aniapp.pokerdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniapp.pokerdemo.data.Card
import com.aniapp.pokerdemo.data.Suit
import com.aniapp.pokerdemo.data.cardSize

@Composable
fun CardView(card: Card?, modifier: Modifier = Modifier) {
    val size = cardSize()
    val rankFont = (size.value * 0.22).sp
    val suitFont = (size.value * 0.28).sp
    val centerSuitFont = (size.value * 0.45).sp

    if (card == null) {
        CardViewBack(modifier.size(size, size * 1.35f))
        return
    }

    val rankStr = card.rank.toCardRank().uppercase()
    val suitSymbol = when(card.suit){
        Suit.SPADES -> "♠"
        Suit.HEARTS -> "♥"
        Suit.DIAMONDS->"♦"
        Suit.CLUBS->"♣"
    }
    val suitColor = when(card.suit){
        Suit.SPADES, Suit.CLUBS -> Color.Black
        Suit.HEARTS, Suit.DIAMONDS -> Color.Red
    }

    Box(
        modifier = modifier
            .size(size, size * 1.35f)  // ⬅️自适应卡片
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(size * 0.1f)
    ) {
        // 左上
        Column(
            Modifier.align(Alignment.TopStart)
        ) {
            Text(rankStr, fontSize = rankFont, color = suitColor, fontWeight = FontWeight.Bold)
            Text(suitSymbol, fontSize = suitFont, color = suitColor)
        }

        // 中心大图
        Text(
            suitSymbol,
            fontSize = centerSuitFont,
            color = suitColor,
            modifier = Modifier.align(Alignment.Center)
        )

        // 右下（旋转180°）
        Column(
            Modifier.align(Alignment.BottomEnd).rotate(180f)
        ) {
            Text(rankStr, fontSize = rankFont, color = suitColor, fontWeight = FontWeight.Bold)
            Text(suitSymbol, fontSize = suitFont, color = suitColor)
        }
    }
}
