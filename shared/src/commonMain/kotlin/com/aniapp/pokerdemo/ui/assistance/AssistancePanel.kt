package com.aniapp.pokerdemo.ui.assistance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniapp.pokerdemo.analysis.ActionEstimate
import com.aniapp.pokerdemo.analysis.AssistanceAnalysis
import com.aniapp.pokerdemo.analysis.EquityMethod
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.ui.theme.Gold
import com.aniapp.pokerdemo.ui.theme.TableSurface
import kotlin.math.roundToInt

@Composable
fun AssistanceSummary(
    message: String? = null,
    analysis: AssistanceAnalysis? = null,
    isPlayerTurn: Boolean,
    onDetails: () -> Unit
) {
    val summary = if (analysis != null) {
        val hand = (listOf(analysis.hand.label) + analysis.hand.draws).joinToString(" · ")
        val recommendation = analysis.recommendation
            ?.let { recommended -> analysis.actions.firstOrNull { it.action == recommended.action }?.label }
        buildString {
            append(hand)
            append(" · 胜率 ").append(formatPercent(analysis.equity.value))
            if (isPlayerTurn && recommendation != null) append(" · 建议 ").append(recommendation)
        }
    } else message.orEmpty()

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .background(TableSurface, RoundedCornerShape(10.dp))
            .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .then(if (analysis != null) Modifier.clickable(onClick = onDetails) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            summary,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (analysis != null) {
            Text("EV 详情", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistanceBottomSheet(analysis: AssistanceAnalysis, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TableSurface,
        contentColor = Color.White
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AI 辅助", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("本地数值估计 · 不会自动操作", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)

            Text("当前牌型", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            Text(
                (listOf(analysis.hand.label) + analysis.hand.draws).joinToString(" · "),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text("当前胜率", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            Text(formatPercent(analysis.equity.value), color = Gold, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            val method = when (analysis.equity.method) {
                EquityMethod.EXACT_HEADS_UP_RIVER -> "河牌单挑 · 随机对手组合精确枚举"
                EquityMethod.MONTE_CARLO -> "蒙特卡洛估计"
            }
            val uncertainty = analysis.equity.margin95?.let { " · 误差约 ±${formatPercent(it)}" }.orEmpty()
            Text(
                "$method · ${analysis.equity.simulations} 次$uncertainty",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp
            )

            analysis.recommendation?.let { recommendation ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF10241C), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("推荐操作", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    val label = analysis.actions.firstOrNull { it.action == recommendation.action }?.label
                        ?: actionName(recommendation.action)
                    Text(label, color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(recommendation.reason, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }

            if (analysis.actions.isNotEmpty()) {
                Text("各操作参考 EV", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                analysis.actions.forEach { estimate ->
                    ActionEvRow(estimate, estimate.action == analysis.recommendation?.action)
                }
            }

            Text(
                "参考 EV 假设对手立即跟注并就此摊牌；未模拟后续下注、弃牌、位置和边池。" +
                    "当前胜率基于随机对手范围，推荐不等于参考 EV 最高，仅供决策参考。",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ActionEvRow(estimate: ActionEstimate, recommended: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (recommended) Gold.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            estimate.label + if (recommended) " · 建议" else "",
            color = if (recommended) Gold else Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            formatReferenceEv(estimate.referenceEv),
            color = if (estimate.referenceEv >= 0) Color(0xFF9ED9B5) else Color(0xFFE9A2A2),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

fun formatPercent(value: Double): String = "${(value.coerceIn(0.0, 1.0) * 100).roundToInt()}%"

fun formatReferenceEv(value: Double): String {
    val rounded = value.roundToInt()
    return "EV ${if (rounded >= 0) "+" else ""}$rounded"
}

private fun actionName(action: PlayerAction): String = when (action) {
    PlayerAction.Fold -> "弃牌"
    PlayerAction.Check -> "过牌"
    PlayerAction.Call -> "跟注"
    is PlayerAction.RaiseTo -> "加注到 ${action.amount}"
}
