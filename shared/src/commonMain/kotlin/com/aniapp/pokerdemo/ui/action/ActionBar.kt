package com.aniapp.pokerdemo.ui.action

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniapp.pokerdemo.analysis.AssistanceAnalysis
import com.aniapp.pokerdemo.analysis.AdviceSituation
import com.aniapp.pokerdemo.analysis.referenceEv
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.session.GameUiState
import com.aniapp.pokerdemo.ui.theme.FoldButton
import com.aniapp.pokerdemo.ui.theme.Gold
import com.aniapp.pokerdemo.ui.theme.TableSurface
import com.aniapp.pokerdemo.ui.assistance.formatReferenceEv
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun ActionBar(
    ui: GameUiState,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier,
    assistance: AssistanceAnalysis? = null
) {
    var pending by remember { mutableStateOf<PlayerAction?>(null) }
    val myTurn = ui.isPlayerTurn && !ui.aiThinking
    val enabled = myTurn && pending == null
    val legal = ui.legal
    val recommended = assistance?.recommendation?.action
    var showRaise by remember { mutableStateOf(false) }
    var raiseTo by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    // 服务器快照到达即解除等待；超时兜底防止断线时按钮永久锁死
    LaunchedEffect(ui) { pending = null }
    LaunchedEffect(pending) {
        if (pending != null) {
            delay(2500)
            pending = null
        }
    }
    fun submit(action: PlayerAction) {
        if (!legal.accepts(action)) return
        pending = action
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onAction(action)
    }
    LaunchedEffect(ui.isPlayerTurn, ui.table.phase, ui.table.currentBet, ui.table.handNumber) {
        showRaise = false
    }
    Column(modifier.fillMaxWidth()) {
        if (showRaise && enabled && legal.canRaise) {
            RaisePanel(
                ui = ui,
                assistance = assistance,
                raiseTo = raiseTo,
                onRaiseTo = { raiseTo = it },
                onConfirm = {
                    showRaise = false
                    submit(PlayerAction.RaiseTo(raiseTo))
                }
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { submit(PlayerAction.Fold) },
                enabled = enabled && legal.canFold,
                modifier = Modifier.weight(1f).recommendedBorder(recommended == PlayerAction.Fold),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FoldButton,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF3A3A3A),
                    disabledContentColor = Color.Gray
                )
            ) { ActionLabel("弃牌", pending == PlayerAction.Fold, recommended == PlayerAction.Fold) }
            if (myTurn && legal.canCheck) {
                Button(
                    onClick = { submit(PlayerAction.Check) },
                    modifier = Modifier.weight(1f).recommendedBorder(recommended == PlayerAction.Check)
                ) { ActionLabel("过牌", pending == PlayerAction.Check, recommended == PlayerAction.Check) }
            } else {
                val label = when {
                    !enabled -> "跟注"
                    legal.callAmount > 0 && legal.callAmount >= ui.me.chips -> "跟注全下"
                    else -> "跟注 ${legal.callAmount}"
                }
                Button(
                    onClick = { submit(PlayerAction.Call) },
                    enabled = enabled && legal.canCall,
                    modifier = Modifier.weight(1f).recommendedBorder(recommended == PlayerAction.Call)
                ) { ActionLabel(label, pending == PlayerAction.Call, recommended == PlayerAction.Call) }
            }
            Button(
                onClick = {
                    showRaise = !showRaise
                    raiseTo = (recommended as? PlayerAction.RaiseTo)?.amount ?: legal.minRaiseTo ?: 0
                },
                enabled = enabled && legal.canRaise,
                modifier = Modifier.weight(1f).recommendedBorder(recommended is PlayerAction.RaiseTo)
            ) { ActionLabel(if (showRaise) "收起" else "加注", false, recommended is PlayerAction.RaiseTo) }
        }
    }
}

private val GameUiState.legal get() = table.legal

@Composable
private fun ActionLabel(text: String, busy: Boolean, recommended: Boolean = false) {
    if (busy) {
        CircularProgressIndicator(Modifier.size(16.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (recommended) {
                Text("建议", color = LocalContentColor.current.copy(alpha = 0.75f), fontSize = 9.sp, lineHeight = 11.sp)
            }
        }
    }
}

private fun Modifier.recommendedBorder(recommended: Boolean): Modifier =
    if (recommended) border(2.dp, Gold, RoundedCornerShape(20.dp)) else this

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RaisePanel(
    ui: GameUiState,
    assistance: AssistanceAnalysis?,
    raiseTo: Int,
    onRaiseTo: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    val min = ui.legal.minRaiseTo ?: return
    val max = ui.legal.maxRaiseTo ?: return
    val bb = ui.bigBlind
    val table = ui.table
    val toCall = ui.legal.callAmount
    val situation = remember(ui.table, ui.bigBlind) { AdviceSituation.from(ui.table, ui.bigBlind) }
    val currentReferenceEv = assistance?.let { analysis ->
        situation?.let { referenceEv(it, PlayerAction.RaiseTo(raiseTo), analysis.equity.value) }
    }

    fun quick(fraction: Double): Int {
        val target = table.currentBet + ((table.pot + toCall) * fraction).roundToInt()
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
        if (currentReferenceEv != null) {
            Text(
                formatReferenceEv(currentReferenceEv),
                color = Gold,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
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