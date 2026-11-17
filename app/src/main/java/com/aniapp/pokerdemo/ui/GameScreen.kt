package com.aniapp.pokerdemo.ui

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aniapp.pokerdemo.vm.GameViewModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TexasHoldemGameScreen(vm: GameViewModel) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF006600))
            .padding(12.dp, 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "德州扑克",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(16.dp))

        // 电脑摊牌前
        if (!vm.showComputerCards.value) {
            CardRow(listOf(null, null)) // 两张背面
        } else {
            CardRow(vm.computerHand)
        }

        Spacer(Modifier.height(24.dp))

        // 公共牌
        CardRow(vm.communityCards)

        Spacer(Modifier.height(24.dp))

        // 玩家手牌
        CardRow(vm.playerHand)

        Spacer(Modifier.height(24.dp))

        // 操作按钮
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Row {
                GameButton("开始") { vm.startGame() }
                Spacer(Modifier.width(8.dp))
                GameButton("Flop") { vm.dealFlop() }
                Spacer(Modifier.width(8.dp))
                GameButton("Turn") { vm.dealTurn() }
                Spacer(Modifier.width(8.dp))
                GameButton("River") { vm.dealRiver() }
            }

            Spacer(Modifier.height(8.dp))

            Row {
                GameButton("摊牌") { vm.showdown() }
                Spacer(Modifier.width(8.dp))
                GameButton("重新开始") { vm.restartGame() }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(text = vm.result.value, color = Color.White)
    }
}

@Composable
fun GameButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, shape = RoundedCornerShape(8.dp)) {
        Text(text)
    }
}

// 牌资源转换辅助
fun Int.toCardRank(): String = when (this) {
    in 2..10 -> this.toString()
    11 -> "j"
    12 -> "q"
    13 -> "k"
    14 -> "a"
    else -> ""
}
