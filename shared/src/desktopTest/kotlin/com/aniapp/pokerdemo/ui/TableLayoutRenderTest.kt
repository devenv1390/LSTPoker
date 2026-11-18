package com.aniapp.pokerdemo.ui

import androidx.compose.ui.ImageComposeScene
import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.Suit
import com.aniapp.pokerdemo.net.SeatInfo
import com.aniapp.pokerdemo.net.TableView
import com.aniapp.pokerdemo.session.GameSession
import com.aniapp.pokerdemo.session.GameUiState
import com.aniapp.pokerdemo.ui.theme.PokerDemoTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.test.Test

class TableLayoutRenderTest {

    private class StubSession(state: GameUiState) : GameSession {
        override val uiState: StateFlow<GameUiState> = MutableStateFlow(state)
        override fun act(action: PlayerAction) {}
        override fun nextHand() {}
        override fun newMatch() {}
        override fun dispose() {}
    }

    private fun view(n: Int): TableView {
        val names = listOf("超长昵称十二字甲乙丙丁", "DeskB", "电脑3", "AI-4", "AI-5", "AI-6")
        val seats = (0 until n).map { i ->
            SeatInfo(
                seat = i,
                name = if (i == n - 1) "你" else names[i],
                chips = 1980 - i * 10,
                streetBet = if (i == 0) 20 else if (i == 1) 40 else 0,
                totalCommitted = if (i == 0) 20 else if (i == 1) 40 else 0,
                folded = n >= 4 && i == 3,
                allIn = i == 1,
                connected = i != 2,
                isAi = i >= 3
            )
        }
        return TableView(
            seats = seats,
            board = listOf(Card(14, Suit.CLUBS), Card(5, Suit.DIAMONDS), Card(9, Suit.HEARTS)),
            dealer = 0,
            acting = 1,
            phase = Phase.FLOP,
            handNumber = 3,
            currentBet = 40,
            minRaiseTo = 80,
            pot = 60,
            mySeat = n - 1,
            myHole = listOf(Card(12, Suit.CLUBS), Card(9, Suit.HEARTS)),
            legal = LegalActions()
        )
    }

    private fun png(scene: ImageComposeScene, name: String, nanoTime: Long) {
        val dir = File("build/render").apply { mkdirs() }
        var t = nanoTime
        repeat(150) {
            scene.render(t)
            t += 16_666_667L
            Thread.sleep(12)
        }
        val bytes = scene.render(t).encodeToData()!!.bytes
        File(dir, name).writeBytes(bytes)
    }

    @Test
    fun renderAllSeatCountsBothViewports() {
        var clock = 1_000_000_000L
        for (n in 2..6) {
            for ((w, h, tag) in listOf(Triple(360, 780, "phone"), Triple(1280, 800, "desk"))) {
                val scene = ImageComposeScene(w, h) {
                    PokerDemoTheme { GameScreen(StubSession(GameUiState(table = view(n)))) }
                }
                try {
                    png(scene, "layout-$n-$tag.png", clock)
                    clock += 3_000_000_000L
                } finally {
                    scene.close()
                }
            }
        }
    }
}