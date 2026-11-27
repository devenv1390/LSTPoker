package com.aniapp.pokerdemo

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.engine.Deck
import com.aniapp.pokerdemo.engine.Suit
import com.aniapp.pokerdemo.engine.createDeck
import com.aniapp.pokerdemo.ui.App
import com.aniapp.pokerdemo.ui.theme.PokerDemoTheme
import com.aniapp.pokerdemo.update.UpdateCoordinator
import com.aniapp.pokerdemo.update.UpdateDialog

class MainActivity : ComponentActivity() {
    private val updateCoordinator by lazy { UpdateCoordinator(this, lifecycleScope) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val scenario = intent.getStringExtra("scenario")
        val scenarioParts = scenario?.split("|")?.takeIf { it.size >= 3 }
        setContent {
            val updateState by updateCoordinator.state.collectAsState()
            val installSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                updateCoordinator.onInstallPermissionResult()
            }
            PokerDemoTheme {
                App(
                    versionText = stringResource(R.string.app_version),
                    logo = {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = 1.45f
                                scaleY = 1.45f
                            }
                        )
                    },
                    autoStartLocal = scenarioParts != null,
                    autoStartAiCount = scenarioParts?.size?.minus(2) ?: 1,
                    scenarioDeck = scenarioDeck(scenarioParts),
                    scenarioChips = scenarioChips(),
                    backHandler = { onBack -> BackHandler(onBack = onBack) },
                    onServerAddress = updateCoordinator::rememberServerAddress,
                    onCheckUpdate = updateCoordinator::checkManually,
                    updateOverlay = {
                        UpdateDialog(
                            state = updateState,
                            onConfirm = updateCoordinator::confirmDownload,
                            onDismiss = updateCoordinator::dismiss,
                            onOpenSettings = { installSettings.launch(updateCoordinator.installSettingsIntent()) },
                            onRetry = updateCoordinator::retry
                        )
                    }
                )
            }
        }
        updateCoordinator.checkForUpdate(silent = true)
    }

    override fun onDestroy() {
        updateCoordinator.close()
        super.onDestroy()
    }

    // 真机验证入口：仅 debug 包生效
    // adb am start --es scenario 'As Ah|Ks Kh|2c 3d 4h 5s 6c' [--es chips '500,1000,2000']
    // scenario 为各座位手牌（首段为玩家）加末段公共牌；chips 可选，按座位指定起始筹码
    private fun scenarioDeck(parts: List<String>?): (() -> Deck)? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        if (parts == null) return null
        val cards = scenarioCards(parts.dropLast(1), parts.last())
        return { Deck(cards) }
    }

    private fun scenarioChips(): List<Int>? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        val raw = intent.getStringExtra("chips") ?: return null
        val chips = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        return chips.takeIf { it.isNotEmpty() }
    }
}

private fun scenarioCards(holes: List<String>, board: String): List<Card> {
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

    val holeCards = holes.flatMap { parse(it) }
    val boardCards = parse(board)
    val burns = createDeck().filterNot { it in holeCards || it in boardCards }.take(3)
    return holeCards + listOf(
        burns[0], boardCards[0], boardCards[1], boardCards[2],
        burns[1], boardCards[3],
        burns[2], boardCards[4],
    )
}