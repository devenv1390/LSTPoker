package com.aniapp.pokerdemo.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniapp.pokerdemo.engine.ai.AiDifficulty
import com.aniapp.pokerdemo.ui.theme.CardWhite
import com.aniapp.pokerdemo.ui.theme.Gold

@Composable
fun DifficultyChips(
    selected: AiDifficulty,
    onSelect: (AiDifficulty) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AiDifficulty.entries.forEach { d ->
            val isSelected = d == selected
            Button(
                onClick = { onSelect(d) },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) Gold else Color.White.copy(alpha = 0.08f),
                    contentColor = if (isSelected) Color(0xFF241B04) else CardWhite
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    d.label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
