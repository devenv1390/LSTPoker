package com.aniapp.pokerdemo.ui.table

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.aniapp.pokerdemo.ui.theme.FeltDark
import com.aniapp.pokerdemo.ui.theme.FeltGreen
import com.aniapp.pokerdemo.ui.theme.Gold

@Composable
fun PokerTable(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val railTop = Offset(w * 0.02f, h * 0.10f)
        val railSize = Size(w * 0.96f, h * 0.66f)
        drawOval(Color(0xFF2E1F14), railTop, railSize)
        val inset = w * 0.035f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(FeltGreen, FeltDark),
                center = Offset(w / 2f, h * 0.43f),
                radius = w * 0.6f
            ),
            topLeft = Offset(railTop.x + inset, railTop.y + inset),
            size = Size(railSize.width - inset * 2, railSize.height - inset * 2)
        )
        drawOval(
            color = Gold.copy(alpha = 0.25f),
            topLeft = Offset(railTop.x + inset * 1.8f, railTop.y + inset * 1.8f),
            size = Size(railSize.width - inset * 3.6f, railSize.height - inset * 3.6f),
            style = Stroke(width = 2f)
        )
    }
}