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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniapp.pokerdemo.engine.Card
import com.aniapp.pokerdemo.net.SeatInfo
import com.aniapp.pokerdemo.net.TableView
import com.aniapp.pokerdemo.session.GameSession
import com.aniapp.pokerdemo.ui.action.ActionBar
import com.aniapp.pokerdemo.ui.card.BoardRow
import com.aniapp.pokerdemo.ui.card.CardSize
import com.aniapp.pokerdemo.ui.card.HandRow
import com.aniapp.pokerdemo.ui.overlay.HandResultOverlay
import com.aniapp.pokerdemo.ui.table.PokerTable
import com.aniapp.pokerdemo.ui.table.SeatView
import com.aniapp.pokerdemo.ui.theme.Gold
import com.aniapp.pokerdemo.ui.theme.TableBackground
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun GameScreen(
    session: GameSession,
    notice: String? = null,
    modifier: Modifier = Modifier
) {
    val ui by session.uiState.collectAsState()
    val table = ui.table
    BoxWithConstraints(modifier.fillMaxSize().background(TableBackground)) {
        val cardSize = CardSize(minOf(maxWidth / (if (table.seats.size > 3) 9f else 7.2f), 64.dp))
        PokerTable(Modifier.fillMaxSize())

        val opponents = table.seats.filter { it.seat != table.mySeat }
        // 水平等分槽位保证相邻座位列永不交叠；高度沿椭圆弧分布保留牌桌环坐感
        val slotW = maxWidth / opponents.size
        opponents.forEachIndexed { j, s ->
            val f = (j + 0.5f) / opponents.size
            val x = -maxWidth / 2f + slotW * (j + 0.5f)
            val y = -maxHeight * 0.16f * (1f + sin(PI * f).toFloat())
            val oppCardSize = CardSize(minOf(cardSize.width, (slotW - 14.dp) / 2f))
            SeatView(
                seat = s,
                isDealer = table.dealer == s.seat,
                isActing = table.acting == s.seat && table.phase.isBetting(),
                top = true,
                slotWidth = slotW,
                modifier = Modifier.align(Alignment.Center).offset(x, y),
                cards = {
                    HandRow(
                        cards = opponentHole(table, s),
                        faceUp = table.revealedHoles.containsKey(s.seat),
                        size = oppCardSize,
                        handNumber = table.handNumber
                    )
                }
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val header = if (ui.online && ui.roomCode != null) {
                "房间 ${ui.roomCode} · 第 ${table.handNumber} 手 · 盲注 ${ui.smallBlind}/${ui.bigBlind}"
            } else {
                "第 ${table.handNumber} 手 · 盲注 ${ui.smallBlind}/${ui.bigBlind}"
            }
            Text(header, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            if (notice != null) {
                Text(notice, color = Gold, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            val pot by animateIntAsState(table.pot, tween(400), label = "pot")
            Text("底池 $pot", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            BoardRow(table.board, cardSize, table.handNumber)
            Spacer(Modifier.weight(1f))
            SeatView(
                seat = ui.me,
                isDealer = table.dealer == table.mySeat,
                isActing = ui.isPlayerTurn,
                top = false,
                cards = {
                    HandRow(
                        cards = table.myHole,
                        faceUp = true,
                        size = cardSize,
                        handNumber = table.handNumber
                    )
                }
            )
            Spacer(Modifier.height(8.dp))
            val thinking = when {
                ui.aiThinking -> "电脑思考中…"
                table.phase.isBetting() && table.acting != null && table.acting != table.mySeat ->
                    "${ui.seatName(table.acting!!)} 思考中…"
                else -> null
            }
            if (thinking != null) {
                Text(
                    thinking,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.height(16.dp)
                )
            } else {
                Spacer(Modifier.height(16.dp))
            }
            ActionBar(ui = ui, onAction = session::act)
        }
        HandResultOverlay(ui, onNextHand = session::nextHand, onNewMatch = session::newMatch)
    }
}

private fun opponentHole(table: TableView, seat: SeatInfo): List<Card?> = when {
    seat.folded -> emptyList()
    table.revealedHoles.containsKey(seat.seat) -> table.revealedHoles.getValue(seat.seat)
    else -> listOf(null, null)
}