package com.aniapp.pokerdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.aniapp.pokerdemo.ui.TexasHoldemGameScreen
import com.aniapp.pokerdemo.vm.GameViewModel

class MainActivity : ComponentActivity() {
    private val gameViewModel by viewModels<GameViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TexasHoldemGameScreen(gameViewModel) }
    }
}