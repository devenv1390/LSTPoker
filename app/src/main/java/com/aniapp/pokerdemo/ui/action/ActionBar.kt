package com.aniapp.pokerdemo.ui.action

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.Seat
import com.aniapp.pokerdemo.ui.theme.FoldButton
import com.aniapp.pokerdemo.ui.theme.Gold
import com.aniapp.pokerdemo.ui.theme.TableSurface
import com.aniapp.pokerdemo.vm.GameUiState
import kotlin.math.roundToInt

@Composable
fun ActionBar(
    ui: GameUiState,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = ui.isPlayerTurn && !ui.aiThinking
    val legal = ui.legal
    var showRaise by remember { mutableStateOf(false) }
    var raiseTo by remember { mutableIntStateOf(0) }
    LaunchedEffect(ui.isPlayerTurn, ui.game.phase, ui.game.currentBet, ui.game.handNumber) {
        showRaise = false
    }
    Column(modifier.fillMaxWidth()) {
        if (showRaise && enabled && legal.canRaise) {
            RaisePanel(
                ui = ui,
                raiseTo = raiseTo,
                onRaiseTo = { raiseTo = it },
                onConfirm = {
                    showRaise = false
                    onAction(PlayerAction.RaiseTo(raiseTo))
                }
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onAction(PlayerAction.Fold) },
                enabled = enabled && legal.canFold,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FoldButton,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF3A3A3A),
                    disabledContentColor = Color.Gray
                )
            ) { Text("弃牌", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (enabled && legal.canCheck) {
                Button(
                    onClick = { onAction(PlayerAction.Check) },
                    modifier = Modifier.weight(1f)
                ) { Text("过牌", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else {
                val label = when {
                    !enabled -> "跟注"
                    legal.callAmount > 0 && legal.callAmount >= ui.game.of(Seat.PLAYER).chips -> "跟注全下"
                    else -> "跟注 ${legal.callAmount}"
                }
                Button(
                    onClick = { onAction(PlayerAction.Call) },
                    enabled = enabled && legal.canCall,
                    modifier = Modifier.weight(1f)
                ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Button(
                onClick = {
                    showRaise = !showRaise
                    raiseTo = legal.minRaiseTo ?: 0
                },
                enabled = enabled && legal.canRaise,
                modifier = Modifier.weight(1f)
            ) { Text(if (showRaise) "收起" else "加注", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RaisePanel(
    ui: GameUiState,
    raiseTo: Int,
    onRaiseTo: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    val min = ui.legal.minRaiseTo ?: return
    val max = ui.legal.maxRaiseTo ?: return
    val bb = ui.bigBlind
    val game = ui.game
    val toCall = ui.legal.callAmount

    fun quick(fraction: Double): Int {
        val target = game.currentBet + ((game.pot + toCall) * fraction).roundToInt()
        return ((target / bb) * bb).coerceIn(min, max)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(TableSurface, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickButton("½底池", Modifier.weight(1f)) { onRaiseTo(quick(0.5)) }
            QuickButton("底池", Modifier.weight(1f)) { onRaiseTo(quick(1.0)) }
            QuickButton("全下", Modifier.weight(1f)) { onRaiseTo(max) }
        }
        if (max > min) {
            Slider(
                value = raiseTo.toFloat().coerceIn(min.toFloat(), max.toFloat()),
                onValueChange = {
                    onRaiseTo(((it / bb).roundToInt() * bb).coerceIn(min, max))
                },
                valueRange = min.toFloat()..max.toFloat(),
                steps = ((max - min) / bb - 1).coerceAtLeast(0),
                thumb = {
                    Box(
                        Modifier
                            .width(12.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Gold)
                    )
                },
                track = { state ->
                    val fraction = (state.value - min) / (max - min)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF39463F))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(Gold)
                        )
                    }
                }
            )
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF1B1B1B))
        ) {
            Text(if (raiseTo >= max) "全下 $raiseTo" else "加注到 $raiseTo")
        }
    }
}

@Composable
private fun QuickButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}