package com.aniapp.pokerdemo.ui.table

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniapp.pokerdemo.net.SeatInfo
import com.aniapp.pokerdemo.ui.theme.ChipRed
import com.aniapp.pokerdemo.ui.theme.Gold

@Composable
fun SeatView(
    seat: SeatInfo,
    isDealer: Boolean,
    isActing: Boolean,
    top: Boolean,
    modifier: Modifier = Modifier,
    slotWidth: Dp = Dp.Unspecified,
    cards: @Composable () -> Unit
) {
    val bounded = slotWidth != Dp.Unspecified
    val stacked = bounded && slotWidth < 160.dp
    val tiny = bounded && slotWidth < 110.dp
    Column(
        modifier
            .then(if (bounded) Modifier.width(slotWidth) else Modifier)
            .alpha(if (seat.folded) 0.4f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!top) BetChips(seat.streetBet, tiny)
        if (top) SeatInfoRow(seat, isDealer, isActing, stacked, tiny, slotWidth)
        Spacer(Modifier.height(if (tiny) 2.dp else 4.dp))
        cards()
        Spacer(Modifier.height(if (tiny) 2.dp else 4.dp))
        if (top) BetChips(seat.streetBet, tiny) else SeatInfoRow(seat, isDealer, isActing, stacked, tiny, slotWidth)
    }
}

@Composable
private fun SeatInfoRow(
    seat: SeatInfo,
    isDealer: Boolean,
    isActing: Boolean,
    stacked: Boolean,
    tiny: Boolean,
    slotWidth: Dp
) {
    val chips by animateIntAsState(seat.chips, tween(500), label = "chips")
    val frame = if (isActing) {
        Modifier
            .border(1.dp, Gold.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .padding(horizontal = if (tiny) 3.dp else 8.dp, vertical = 2.dp)
    } else {
        Modifier.padding(horizontal = if (tiny) 3.dp else 8.dp, vertical = 2.dp)
    }
    val nameSize = if (tiny) 11.sp else if (stacked) 12.sp else 14.sp
    val chipSize = if (tiny) 10.sp else if (stacked) 11.sp else 14.sp
    if (stacked) {
        Column(
            frame.then(if (slotWidth != Dp.Unspecified) Modifier.fillMaxWidth() else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                seat.name,
                color = if (isActing) Gold else Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = nameSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (tiny) 2.dp else 4.dp)
            ) {
                Text("$chips", color = Gold, fontWeight = FontWeight.Bold, fontSize = chipSize)
                DealerBadge(isDealer, tiny)
                StatusBadges(seat, short = true)
            }
        }
    } else {
        Row(
            frame.then(if (slotWidth != Dp.Unspecified) Modifier.widthIn(max = slotWidth) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                seat.name,
                color = if (isActing) Gold else Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = nameSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text("$chips", color = Gold, fontWeight = FontWeight.Bold, fontSize = chipSize)
            DealerBadge(isDealer, tiny)
            StatusBadges(seat, short = false)
        }
    }
}

@Composable
private fun DealerBadge(isDealer: Boolean, tiny: Boolean) {
    if (isDealer) {
        Box(
            Modifier.size(if (tiny) 12.dp else 18.dp).background(Gold, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "D",
                fontSize = if (tiny) 8.sp else 10.sp,
                lineHeight = if (tiny) 8.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1B)
            )
        }
    }
}

@Composable
private fun StatusBadges(seat: SeatInfo, short: Boolean) {
    if (seat.allIn) {
        Text(
            if (short) "全" else "全下",
            color = Color.White,
            fontSize = if (short) 9.sp else 10.sp,
            lineHeight = if (short) 11.sp else 12.sp,
            modifier = Modifier
                .background(ChipRed, RoundedCornerShape(4.dp))
                .padding(horizontal = if (short) 2.dp else 4.dp, vertical = 1.dp)
        )
    }
    if (!seat.connected) {
        Text(
            if (short) "托" else "托管",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = if (short) 9.sp else 10.sp,
            lineHeight = if (short) 11.sp else 12.sp,
            modifier = Modifier
                .background(Color(0xFF4A4A4A), RoundedCornerShape(4.dp))
                .padding(horizontal = if (short) 2.dp else 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun BetChips(amount: Int, tiny: Boolean) {
    Box(Modifier.height(if (tiny) 16.dp else 22.dp), contentAlignment = Alignment.Center) {
        if (amount > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(Modifier.size(if (tiny) 10.dp else 12.dp)) {
                    drawCircle(ChipRed)
                    drawCircle(Color.White, radius = size.minDimension / 4f)
                }
                Text(
                    "$amount",
                    color = Color.White,
                    fontSize = if (tiny) 10.sp else 12.sp,
                    lineHeight = if (tiny) 12.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}