package com.aniapp.pokerdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniapp.pokerdemo.data.cardSize

@Composable
fun CardViewBack(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(cardSize() * 0.9f, cardSize() * 1.3f)
            .padding(4.dp)
            .shadow(6.dp, RoundedCornerShape(10.dp))
            .background(Color(0xFF003366), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("★", color = Color.White, fontSize = 32.sp)
    }
}
