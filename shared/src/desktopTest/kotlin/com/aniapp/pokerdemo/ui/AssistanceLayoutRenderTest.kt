package com.aniapp.pokerdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import com.aniapp.pokerdemo.analysis.AssistanceAnalyzer
import com.aniapp.pokerdemo.analysis.AdviceSituation
import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.Suit
import com.aniapp.pokerdemo.engine.ai.mc.EquityResult
import com.aniapp.pokerdemo.net.SeatInfo
import com.aniapp.pokerdemo.net.TableView
import com.aniapp.pokerdemo.session.GameSession
import com.aniapp.pokerdemo.session.GameUiState
import com.aniapp.pokerdemo.ui.assistance.AssistanceBottomSheet
import com.aniapp.pokerdemo.ui.theme.PokerDemoTheme
import com.aniapp.pokerdemo.ui.theme.TableBackground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull

class AssistanceLayoutRenderTest {

    private class StubSession(state: GameUiState) : GameSession {
        override val uiState: StateFlow<GameUiState> = MutableStateFlow(state)
        override fun act(action: PlayerAction) {}
        override fun nextHand() {}
        override fun newMatch() {}
        override fun dispose() {}
    }

    @Test
    fun renderEnabledAssistanceAcrossSeatCountsAndViewports() {
        var clock = 4_000_000_000L
        for (n in listOf(2, 6)) {
            for ((w, h, tag) in listOf(Triple(360, 780, "phone"), Triple(640, 360, "landscape"))) {
                val scene = ImageComposeScene(w, h) {
                    PokerDemoTheme {
                        GameScreen(StubSession(GameUiState(table = view(n))), initialAssistanceEnabled = true)
                    }
                }
                try {
                    png(scene, "assistance-$n-$tag.png", clock)
                    clock += 3_000_000_000L
                } finally {
                    scene.close()
                }
            }
        }
    }

    @Test
    fun renderAssistanceDetailsSheet() {
        val analysis = AssistanceAnalyzer().analyze(
            assertNotNull(AdviceSituation.from(view(2), 20)),
            EquityResult(0.64, 1200)
        )
        val scene = ImageComposeScene(360, 780) {
            PokerDemoTheme {
                Box(Modifier.fillMaxSize().background(TableBackground)) {
                    AssistanceBottomSheet(analysis, onDismiss = {})
                }
            }
        }
        try {
            png(scene, "assistance-details.png", 8_000_000_000L)
        } finally {
            scene.close()
        }
    }

    private fun png(scene: ImageComposeScene, name: String, nanoTime: Long) {
        val dir = File("build/render").apply { mkdirs() }
        var t = nanoTime
        repeat(180) {
            scene.render(t)
            t += 16_666_667L
            Thread.sleep(12)
        }
        val bytes = scene.render(t).encodeToData()!!.bytes
        File(dir, name).writeBytes(bytes)
    }

    private fun view(n: Int): TableView {
        val hero = n - 1
        val seats = (0 until n).map { i ->
            val folded = n >= 4 && i == 3
            val streetBet = when {
                i == hero -> 0
                folded -> 0
                i == 0 -> 20
                i == 1 -> 40
                else -> 0
            }
            SeatInfo(
                seat = i,
                name = if (i == hero) "你" else "玩家$i",
                chips = if (i == hero) 1000 else if (i == 1) 0 else 980 - i * 10,
                streetBet = streetBet,
                totalCommitted = streetBet,
                folded = folded,
                allIn = i == 1 && i != hero,
                connected = i != 2,
                isAi = i != hero
            )
        }
        return TableView(
            seats = seats,
            board = listOf(Card(14, Suit.CLUBS), Card(5, Suit.DIAMONDS), Card(8, Suit.HEARTS)),
            dealer = 0,
            acting = hero,
            phase = Phase.FLOP,
            handNumber = 3,
            currentBet = 40,
            minRaiseTo = 80,
            pot = 60,
            mySeat = hero,
            myHole = listOf(Card(12, Suit.CLUBS), Card(9, Suit.HEARTS)),
            legal = LegalActions(canFold = true, callAmount = 40, minRaiseTo = 80, maxRaiseTo = 1000)
        )
    }
}
