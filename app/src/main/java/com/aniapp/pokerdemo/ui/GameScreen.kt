package com.aniapp.pokerdemo.ui

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniapp.pokerdemo.engine.Seat
import com.aniapp.pokerdemo.ui.action.ActionBar
import com.aniapp.pokerdemo.ui.card.BoardRow
import com.aniapp.pokerdemo.ui.card.CardSize
import com.aniapp.pokerdemo.ui.card.HandRow
import com.aniapp.pokerdemo.ui.overlay.HandResultOverlay
import com.aniapp.pokerdemo.ui.table.PokerTable
import com.aniapp.pokerdemo.ui.table.SeatView
import com.aniapp.pokerdemo.ui.theme.Gold
import com.aniapp.pokerdemo.ui.theme.TableBackground
import com.aniapp.pokerdemo.vm.GameViewModel

@Composable
fun TexasHoldemGameScreen(vm: GameViewModel) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize().background(TableBackground)) {
        val cardSize = CardSize(minOf(maxWidth / 7.2f, 64.dp))
        PokerTable(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "第 ${ui.game.handNumber} 手 · 盲注 ${ui.smallBlind}/${ui.bigBlind}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(4.dp))
            SeatView(
                name = "电脑",
                player = ui.game.ai,
                isDealer = ui.game.dealer == Seat.AI,
                top = true,
                cards = {
                    HandRow(
                        cards = ui.game.ai.hole,
                        faceUp = ui.game.aiRevealed,
                        size = cardSize,
                        handNumber = ui.game.handNumber
                    )
                }
            )
            Spacer(Modifier.weight(1f))
            val pot by animateIntAsState(ui.game.pot, tween(400), label = "pot")
            Text("底池 $pot", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            BoardRow(ui.game.board, cardSize, ui.game.handNumber)
            Spacer(Modifier.weight(1f))
            SeatView(
                name = "你",
                player = ui.game.player,
                isDealer = ui.game.dealer == Seat.PLAYER,
                top = false,
                cards = {
                    HandRow(
                        cards = ui.game.player.hole,
                        faceUp = true,
                        size = cardSize,
                        handNumber = ui.game.handNumber
                    )
                }
            )
            Spacer(Modifier.height(8.dp))
            if (ui.aiThinking) {
                Text(
                    "电脑思考中…",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.height(16.dp)
                )
            } else {
                Spacer(Modifier.height(16.dp))
            }
            ActionBar(ui = ui, onAction = vm::onPlayerAction)
        }
        HandResultOverlay(ui, onNextHand = vm::onNextHand, onNewMatch = vm::onNewMatch)
    }
}