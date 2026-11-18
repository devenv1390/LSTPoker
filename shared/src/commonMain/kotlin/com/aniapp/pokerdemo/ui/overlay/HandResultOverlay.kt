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
import com.aniapp.pokerdemo.session.GameUiState
import com.aniapp.pokerdemo.ui.theme.Gold
import com.aniapp.pokerdemo.ui.theme.TableSurface

@Composable
fun HandResultOverlay(
    ui: GameUiState,
    onNextHand: () -> Unit,
    onNewMatch: () -> Unit,
    onBackToLobby: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val phase = ui.table.phase
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
            val result = ui.table.result
            Column(
                Modifier
                    .fillMaxWidth(0.8f)
                    .background(TableSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (result != null) {
                    val winners = result.awards.flatMap { it.winners }.distinct()
                    val iWon = ui.table.mySeat in winners
                    val title = when {
                        iWon && winners.size == 1 -> "你赢了！"
                        iWon -> "平分底池"
                        winners.size == 1 -> "${ui.seatName(winners[0])} 赢了"
                        else -> "平分底池"
                    }
                    Text(
                        title,
                        color = if (iWon) Gold else Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("底池 ${result.totalPot}", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    result.awards.forEachIndexed { i, award ->
                        val label = if (result.awards.size == 1) "主池" else if (i == 0) "主池" else "边池 $i"
                        val names = award.winners.joinToString("、") { ui.seatName(it) }
                        Text(
                            "$label ${award.amount} → $names",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (result.reason == EndReason.SHOWDOWN) {
                        result.revealed.forEach { (seat, value) ->
                            Text(
                                "${ui.seatName(seat)}：${value.category.label}",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        Text(
                            "其余玩家弃牌",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (phase == Phase.MATCH_OVER) {
                    val alive = ui.table.seats.filter { it.chips > 0 }
                    val matchText = when {
                        alive.singleOrNull()?.seat == ui.table.mySeat -> "你赢光了所有对手的筹码！"
                        ui.me.chips == 0 -> "你输光了筹码"
                        else -> "${alive.firstOrNull()?.name ?: "对手"} 赢得了整场比赛"
                    }
                    Text(matchText, color = Gold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    if (ui.online && !ui.isHost) {
                        Text("等待房主开始新对局…", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                    } else {
                        Button(
                            onClick = onNewMatch,
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF1B1B1B))
                        ) { Text("再来一局") }
                    }
                } else {
                    if (ui.online && !ui.isHost) {
                        Text("等待房主开始下一手…", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                    } else {
                        Button(
                            onClick = onNextHand,
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF1B1B1B))
                        ) { Text("下一手") }
                    }
                }
                if (ui.online && onBackToLobby != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "返回大厅",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { onBackToLobby() }
                    )
                }
            }
        }
    }
}