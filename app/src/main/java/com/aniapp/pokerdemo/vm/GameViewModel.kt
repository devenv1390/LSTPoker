package com.aniapp.pokerdemo.vm

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.aniapp.pokerdemo.data.Card
import com.aniapp.pokerdemo.data.bestHand
import com.aniapp.pokerdemo.data.createDeck

class GameViewModel : ViewModel() {
    private lateinit var deck: MutableList<Card>
    val playerHand = mutableStateListOf<Card>()
    val computerHand = mutableStateListOf<Card>()
    val communityCards = mutableStateListOf<Card>()
    val result = mutableStateOf("请开始游戏")
    val showComputerCards = mutableStateOf(false)

    fun startGame() {
        deck = createDeck().shuffled().toMutableList()
        playerHand.clear()
        computerHand.clear()
        communityCards.clear()
        result.value = "游戏进行中..."

        repeat(2) { playerHand += deck.removeFirst() }
        repeat(2) { computerHand += deck.removeFirst() }
    }

    fun dealFlop() {
        if (communityCards.isEmpty()) {
            repeat(3) { communityCards += deck.removeFirst() }
        }
    }

    fun dealTurn() {
        if (communityCards.size == 3) {
            communityCards += deck.removeFirst()
        }
    }

    fun dealRiver() {
        if (communityCards.size == 4) {
            communityCards += deck.removeFirst()
        }
    }

    fun showdown() {
        if (communityCards.size == 5) {
            showComputerCards.value = true
            val pBest = bestHand(playerHand + communityCards)
            val cBest = bestHand(computerHand + communityCards)
            result.value = when {
                pBest > cBest -> "你赢了! ($pBest)"
                cBest > pBest -> "电脑赢了! ($cBest)"
                else -> "平局 ($pBest)"
            }
        } else {
            result.value = "请发完所有公共牌再摊牌"
        }
    }

    fun restartGame() {
        showComputerCards.value = false
        startGame()
        result.value = "已重新开始"
    }
}


sealed class PlayerAction {
    object Fold : PlayerAction()
    object Call : PlayerAction()
    data class Raise(val to: Int) : PlayerAction()
}
