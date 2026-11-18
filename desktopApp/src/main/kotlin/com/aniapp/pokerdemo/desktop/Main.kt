package com.aniapp.pokerdemo.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aniapp.pokerdemo.ui.App
import com.aniapp.pokerdemo.ui.theme.PokerDemoTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "LSTPoker",
        state = rememberWindowState(width = 1280.dp, height = 800.dp)
    ) {
        PokerDemoTheme { App() }
    }
}