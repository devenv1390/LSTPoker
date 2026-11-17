package com.aniapp.pokerdemo.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniapp.pokerdemo.engine.EndReason
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.Seat
import com.aniapp.pokerdemo.ui.theme.Gold
import com.aniapp.pokerdemo.ui.theme.TableSurface
import com.aniapp.pokerdemo.vm.GameUiState

@Composable
fun HandResultOverlay(
    ui: GameUiState,
    onNextHand: () -> Unit,
    onNewMatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val phase = ui.game.phase
    AnimatedVisibility(
        visible = phase == Phase.HAND_OVER || phase == Phase.MATCH_OVER,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            contentAlignment = Alignment.Center
        ) {
            val result = ui.game.result
            Column(
                Modifier
                    .fillMaxWidth(0.8f)
                    .background(TableSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (result != null) {
                    val title = when (result.winner) {
                        Seat.PLAYER -> "你赢了！"
                        Seat.AI -> "电脑赢了"
                        null -> "平分底池"
                    }
                    Text(
                        title,
                        color = if (result.winner == Seat.PLAYER) Gold else Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("底池 ${result.potAwarded}", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    if (result.reason == EndReason.SHOWDOWN && result.playerValue != null && result.aiValue != null) {
                        Text(
                            "你：${result.playerValue.category.label}    电脑：${result.aiValue.category.label}",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            if (result.winner == Seat.PLAYER) "电脑弃牌" else "你弃牌",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (phase == Phase.MATCH_OVER) {
                    Text(
                        if (ui.game.player.chips > 0) "你赢光了电脑的筹码！" else "你输光了筹码",
                        color = Gold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onNewMatch,
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF1B1B1B))
                    ) { Text("再来一局") }
                } else {
                    Button(
                        onClick = onNextHand,
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF1B1B1B))
                    ) { Text("下一手") }
                }
            }
        }
    }
}