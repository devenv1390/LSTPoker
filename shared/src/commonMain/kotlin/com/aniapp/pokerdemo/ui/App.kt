package com.aniapp.pokerdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniapp.pokerdemo.engine.Deck
import com.aniapp.pokerdemo.net.ServerAddress
import com.aniapp.pokerdemo.session.LocalSession
import com.aniapp.pokerdemo.session.OnlineConfig
import com.aniapp.pokerdemo.session.OnlineSession
import com.aniapp.pokerdemo.ui.lobby.LobbyForm
import com.aniapp.pokerdemo.ui.lobby.LobbyView
import com.aniapp.pokerdemo.engine.ai.AiDifficulty
import com.aniapp.pokerdemo.engine.ai.AiProfiles
import com.aniapp.pokerdemo.ui.start.StartScreen
import com.aniapp.pokerdemo.ui.theme.Gold
import com.aniapp.pokerdemo.ui.theme.TableBackground
import kotlinx.coroutines.delay

sealed interface AppScreen {
    data object Start : AppScreen
    data class Local(val aiCount: Int, val difficulty: AiDifficulty = AiDifficulty.STANDARD) : AppScreen
    data object OnlineForm : AppScreen
    data class Online(val config: OnlineConfig) : AppScreen
}

@Composable
fun App(
    versionText: String = "",
    logo: (@Composable () -> Unit)? = null,
    autoStartLocal: Boolean = false,
    autoStartAiCount: Int = 1,
    scenarioDeck: (() -> Deck)? = null,
    scenarioChips: List<Int>? = null,
    backHandler: (@Composable (onBack: () -> Unit) -> Unit)? = null,
    onServerAddress: (ServerAddress) -> Unit = {},
    onCheckUpdate: (() -> Unit)? = null,
    updateOverlay: @Composable () -> Unit = {}
) {
    var screen by remember {
        mutableStateOf<AppScreen>(if (autoStartLocal) AppScreen.Local(autoStartAiCount) else AppScreen.Start)
    }
    val scope = rememberCoroutineScope()

    if (screen !is AppScreen.Start) {
        backHandler?.invoke { screen = AppScreen.Start }
    }

    when (val s = screen) {
        AppScreen.Start -> Box(Modifier.fillMaxSize()) {
            StartScreen(
                onLocalStart = { a, d -> screen = AppScreen.Local(a, d) },
                onOnlineStart = { screen = AppScreen.OnlineForm },
                versionText = versionText,
                logo = logo,
                onCheckUpdate = onCheckUpdate
            )
            updateOverlay()
        }
        is AppScreen.Local -> {
            val session = remember(s.aiCount, s.difficulty) {
                LocalSession(
                    scope = scope,
                    aiCount = s.aiCount,
                    ai = AiProfiles.local(s.difficulty),
                    engine = if (scenarioDeck != null || scenarioChips != null) {
                        LocalSession.defaultEngine(s.aiCount, scenarioDeck, scenarioChips)
                    } else {
                        LocalSession.defaultEngine(s.aiCount)
                    }
                )
            }
            DisposableEffect(session) { onDispose { session.dispose() } }
            GameScreen(session)
        }
        AppScreen.OnlineForm -> LobbyForm(
            onConnect = {
                onServerAddress(ServerAddress(it.host, it.port, it.secure))
                screen = AppScreen.Online(it)
            },
            onBack = { screen = AppScreen.Start }
        )
        is AppScreen.Online -> {
            val session = remember(s.config) { OnlineSession(s.config) }
            DisposableEffect(session) { onDispose { session.dispose() } }
            val phase by session.phase.collectAsState()
            val error by session.error.collectAsState()
            LaunchedEffect(error) {
                if (error != null) {
                    delay(3000)
                    session.clearError()
                }
            }
            when (val p = phase) {
                OnlineSession.Phase.Connecting -> CenterMessage {
                    CircularProgressIndicator(color = Gold)
                    Spacer(Modifier.height(12.dp))
                    Text("连接中…", color = Color.White.copy(alpha = 0.7f))
                }
                is OnlineSession.Phase.InLobby -> LobbyView(
                    lobby = p,
                    error = error,
                    onStart = session::nextHand,
                    onAiFill = session::setAiFill,
                    onLeave = { screen = AppScreen.Start }
                )
                OnlineSession.Phase.Playing -> GameScreen(session, notice = error, onBackToLobby = session::backToLobby)
                is OnlineSession.Phase.Ended -> CenterMessage {
                    Text(p.message, color = Color.White, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { screen = AppScreen.Start },
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF241B04))
                    ) { Text("返回") }
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(TableBackground).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) { content() }
    }
}