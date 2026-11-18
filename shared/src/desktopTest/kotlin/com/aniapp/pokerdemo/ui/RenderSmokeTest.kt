package com.aniapp.pokerdemo.ui

import androidx.compose.ui.ImageComposeScene
import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.Deck
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.Suit
import com.aniapp.pokerdemo.session.LocalSession
import com.aniapp.pokerdemo.ui.theme.PokerDemoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test

class RenderSmokeTest {
    private fun png(scene: ImageComposeScene, name: String, nanoTime: Long) {
        val dir = File("build/render").apply { mkdirs() }
        var t = nanoTime
        repeat(120) {
            scene.render(t)
            t += 16_666_667L
        }
        val bytes = scene.render(t).encodeToData()!!.bytes
        File(dir, name).writeBytes(bytes)
    }

    @Test
    fun renderStartScreen() {
        val scene = ImageComposeScene(1280, 800) {
            PokerDemoTheme { App(versionText = "v-test") }
        }
        try {
            png(scene, "start.png", 3_000_000_000L)
        } finally {
            scene.close()
        }
    }

    @Test
    fun renderHandAndShowdown() = runBlocking {
        val deck = {
            Deck(
                listOf(
                    Card(14, Suit.SPADES), Card(14, Suit.HEARTS),
                    Card(13, Suit.SPADES), Card(13, Suit.HEARTS),
                    Card(2, Suit.CLUBS), Card(14, Suit.CLUBS), Card(5, Suit.DIAMONDS), Card(9, Suit.HEARTS),
                    Card(3, Suit.CLUBS), Card(10, Suit.SPADES),
                    Card(4, Suit.CLUBS), Card(2, Suit.SPADES),
                )
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = LocalSession(scope, aiCount = 1, engine = LocalSession.defaultEngine(1, deck))
        val scene = ImageComposeScene(1280, 800) {
            PokerDemoTheme { GameScreen(session) }
        }
        try {
            png(scene, "hand.png", 1_000_000_000L)
            println("RENDER before act: " + session.uiState.value.table)
            session.act(PlayerAction.RaiseTo(2000))
            delay(3000)
            println("RENDER after act: " + session.uiState.value.table)
            png(scene, "showdown.png", 6_000_000_000L)
        } finally {
            scene.close()
            session.dispose()
            scope.cancel()
        }
    }
}