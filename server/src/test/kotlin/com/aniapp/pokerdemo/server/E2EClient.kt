package com.aniapp.pokerdemo.server

import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.net.ClientMessage
import com.aniapp.pokerdemo.net.GameClient
import com.aniapp.pokerdemo.net.PokerJson
import com.aniapp.pokerdemo.net.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlin.system.exitProcess

// 联机端到端脚本客户端：javaexec -Pe2eArgs="create|join name seatCountOrCode host port maxHands disconnectMs minHumans"
fun main(args: Array<String>) {
    runBlocking {
    val role = args[0]
    val name = args[1]
    val arg2 = args[2]
    val host = args[3]
    val port = args[4].toInt()
    val maxHands = args[5].toInt()
    val disconnectMs = args.getOrNull(6)?.toLongOrNull() ?: 0L
    val minHumans = args.getOrNull(7)?.toIntOrNull() ?: 3
    val aiFill = args.getOrNull(8)?.toIntOrNull() ?: 1

    fun log(msg: String) = println("E2E[$name] $msg")

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val client = GameClient(scope)
    client.connect(host, port)
    log("connected")

    var mySeat = -1
    var startedHands = 0
    var disconnectArmed = disconnectMs > 0

    scope.launch {
        delay(240_000)
        log("TIMEOUT")
        exitProcess(2)
    }

    scope.launch {
        client.incoming.collect { msg ->
            log(PokerJson.encodeToString(msg))
            when (msg) {
                is ServerMessage.Joined -> mySeat = msg.mySeat
                is ServerMessage.Lobby -> {
                    if (role == "create" && startedHands == 0 &&
                        msg.seats.count { !it.isAi } >= minHumans
                    ) {
                        startedHands = 1
                        log("START hand 1")
                        client.send(ClientMessage.StartHand)
                    }
                }
                is ServerMessage.Update -> {
                    val v = msg.view
                    if (v.myHole.size != 2) log("VIOLATION myHole=${v.myHole}")
                    if (v.revealedHoles.isNotEmpty() && v.result == null) log("VIOLATION early reveal")
                    if (disconnectArmed) {
                        disconnectArmed = false
                        scope.launch {
                            delay(disconnectMs)
                            log("DISCONNECTING mid-hand")
                            exitProcess(0)
                        }
                    }
                    if (v.result == null && v.acting == mySeat) {
                        delay(250)
                        val action = when {
                            v.legal.canCheck -> PlayerAction.Check
                            v.legal.canCall -> PlayerAction.Call
                            else -> PlayerAction.Fold
                        }
                        client.send(ClientMessage.Act(action))
                    }
                    if (v.result != null && role == "create") {
                        if (startedHands < maxHands) {
                            delay(1500)
                            startedHands++
                            log("START hand $startedHands")
                            client.send(ClientMessage.StartHand)
                        } else {
                            delay(800)
                            log("DONE leaving")
                            client.send(ClientMessage.Leave)
                            delay(500)
                            exitProcess(0)
                        }
                    }
                }
                is ServerMessage.Error -> log("ERROR ${msg.code} ${msg.message}")
                ServerMessage.RoomClosed -> {
                    log("ROOM CLOSED")
                    exitProcess(0)
                }
            }
        }
    }

    if (role == "create") {
        client.send(ClientMessage.CreateRoom(name, arg2.toInt(), aiFill == 1))
    } else {
        client.send(ClientMessage.JoinRoom(arg2, name))
    }
    awaitCancellation()
    }
}