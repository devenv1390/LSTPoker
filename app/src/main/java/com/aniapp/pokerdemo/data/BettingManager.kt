package com.aniapp.pokerdemo.data

class BettingManager(
    var playerChips: Int = 2000,
    var aiChips: Int = 2000,
    var smallBlind: Int = 10,
    var bigBlind: Int = 20
) {
    var pot = 0
    var currentBet = 0
    var playerBet = 0
    var aiBet = 0

    fun postBlinds(isPlayerDealer: Boolean) {
        if (isPlayerDealer) {
            playerPay(smallBlind)
            aiPay(bigBlind)
        } else {
            playerPay(bigBlind)
            aiPay(smallBlind)
        }
    }

    fun playerPay(amount: Int): Boolean {
        if (amount > playerChips) return false
        playerChips -= amount
        playerBet += amount
        pot += amount
        currentBet = maxOf(currentBet, playerBet)
        return true
    }

    fun aiPay(amount: Int) {
        aiChips -= amount
        aiBet += amount
        pot += amount
        currentBet = maxOf(currentBet, aiBet)
    }

    fun playerCall(): Boolean = playerPay(currentBet - playerBet)
    fun aiCall() = aiPay(currentBet - aiBet)
    fun playerRaise(to: Int): Boolean = playerPay(to - playerBet)
    fun aiRaise(to: Int) = aiPay(to - aiBet)

    fun resetBets() {
        playerBet = 0; aiBet = 0; currentBet = 0
    }
}
