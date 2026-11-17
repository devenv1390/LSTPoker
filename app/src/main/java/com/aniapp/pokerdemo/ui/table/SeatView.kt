package com.aniapp.pokerdemo.ui.table

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniapp.pokerdemo.engine.PlayerState
import com.aniapp.pokerdemo.ui.theme.ChipRed
import com.aniapp.pokerdemo.ui.theme.Gold

@Composable
fun SeatView(
    name: String,
    player: PlayerState,
    isDealer: Boolean,
    top: Boolean,
    modifier: Modifier = Modifier,
    cards: @Composable () -> Unit
) {
    Column(
        modifier.alpha(if (player.isFolded) 0.4f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!top) BetChips(player.streetBet)
        if (top) SeatInfo(name, player, isDealer)
        Spacer(Modifier.height(4.dp))
        cards()
        Spacer(Modifier.height(4.dp))
        if (top) BetChips(player.streetBet) else SeatInfo(name, player, isDealer)
    }
}

@Composable
private fun SeatInfo(name: String, player: PlayerState, isDealer: Boolean) {
    val chips by animateIntAsState(player.chips, tween(500), label = "chips")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("$chips", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (isDealer) {
            Box(
                Modifier.size(18.dp).background(Gold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("D", fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1B))
            }
        }
        if (player.isAllIn) {
            Text(
                "全下",
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                modifier = Modifier
                    .background(ChipRed, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun BetChips(amount: Int) {
    Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) {
        if (amount > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(Modifier.size(12.dp)) {
                    drawCircle(ChipRed)
                    drawCircle(Color.White, radius = size.minDimension / 4f)
                }
                Text("$amount", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}