package com.aniapp.pokerdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.Deck
import com.aniapp.pokerdemo.engine.HeadsUpEngine
import com.aniapp.pokerdemo.engine.Suit
import com.aniapp.pokerdemo.engine.createDeck
import com.aniapp.pokerdemo.ui.TexasHoldemGameScreen
import com.aniapp.pokerdemo.ui.start.StartScreen
import com.aniapp.pokerdemo.ui.theme.PokerDemoTheme
import com.aniapp.pokerdemo.vm.GameViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PokerDemoTheme {
                var started by rememberSaveable { mutableStateOf(intent.getStringExtra("scenario") != null) }
                if (started) {
                    val vm: GameViewModel = viewModel(factory = viewModelFactory {
                        initializer { GameViewModel(scenarioEngine()) }
                    })
                    BackHandler { started = false }
                    TexasHoldemGameScreen(vm)
                } else {
                    StartScreen(onStart = { started = true })
                }
            }
        }
    }

    // 真机验证入口：仅 debug 包生效，adb am start --es scenario "As Ah|Ks Kh|2c 3d 4h 5s 6c"
    private fun scenarioEngine(): HeadsUpEngine {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return HeadsUpEngine()
        val scenario = intent.getStringExtra("scenario") ?: return HeadsUpEngine()
        val parts = scenario.split("|")
        return HeadsUpEngine(newDeck = { Deck(scenarioCards(parts[0], parts[1], parts[2])) })
    }
}

private fun scenarioCards(player: String, ai: String, board: String): List<Card> {
    fun parse(text: String): List<Card> = text.split(" ").map { token ->
        val rank = when (val r = token.dropLast(1)) {
            "A" -> 14; "K" -> 13; "Q" -> 12; "J" -> 11; "T" -> 10
            else -> r.toInt()
        }
        val suit = when (token.last()) {
            's' -> Suit.SPADES; 'h' -> Suit.HEARTS; 'd' -> Suit.DIAMONDS
            else -> Suit.CLUBS
        }
        Card(rank, suit)
    }

    val hole = parse(player) + parse(ai)
    val boardCards = parse(board)
    val burns = createDeck().filterNot { it in hole || it in boardCards }.take(3)
    return listOf(
        hole[0], hole[1], hole[2], hole[3],
        burns[0], boardCards[0], boardCards[1], boardCards[2],
        burns[1], boardCards[3],
        burns[2], boardCards[4],
    )
}