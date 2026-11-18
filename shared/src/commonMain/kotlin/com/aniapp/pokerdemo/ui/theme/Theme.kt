package com.aniapp.pokerdemo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PokerColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1B1B1B),
    secondary = FeltGreen,
    onSecondary = Color.White,
    background = TableBackground,
    onBackground = Color.White,
    surface = TableSurface,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E3B33),
    onSurfaceVariant = Color(0xFFCFE0D8)
)

@Composable
fun PokerDemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PokerColorScheme,
        typography = Typography,
        content = content
    )
}