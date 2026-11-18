package com.aniapp.pokerdemo.session

import com.aniapp.pokerdemo.net.ClientMessage
import com.aniapp.pokerdemo.net.GameClient
import com.aniapp.pokerdemo.net.LobbySeat
import com.aniapp.pokerdemo.net.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class OnlineConfig(
    val host: String,
    val port: Int,
    val nickname: String,
    val seatCount: Int? = null,
    val joinCode: String? = null,
    val aiFill: Boolean = true
)

class OnlineSession(
    private val scope: CoroutineScope,
    private val config: OnlineConfig
) : GameSession {

    sealed interface Phase {
        data object Connecting : Phase
        data class InLobby(
            val roomCode: String,
            val seats: List<LobbySeat>,
            val hostSeat: Int,
            val seatCount: Int,
            val mySeat: Int,
            val aiFill: Boolean = true
        ) : Phase
        data object Playing : Phase
        data class Ended(val message: String) : Phase
    }

    private val client = GameClient(scope)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var disposed = false
    private var mySeat = -1
    private var hostSeat = -1
    private var roomCode: String? = null

    private val _phase = MutableStateFlow<Phase>(Phase.Connecting)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _uiState = MutableStateFlow(
        GameUiState(table = emptyTableView(), online = true, isHost = false)
    )
    override val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            try {
                client.connect(config.host, config.port)
                val join = config.joinCode
                if (join != null) client.send(ClientMessage.JoinRoom(join, config.nickname))
                else client.send(ClientMessage.CreateRoom(config.nickname, config.seatCount ?: 6, config.aiFill))
            } catch (e: Exception) {
                _phase.value = Phase.Ended("无法连接服务器：${e.message ?: "网络错误"}")
            }
        }
        scope.launch {
            client.incoming.collect { handle(it) }
        }
        scope.launch {
            client.connected.drop(1).collect { connected ->
                if (!connected && !disposed && _phase.value !is Phase.Ended) {
                    _phase.value = Phase.Ended("与服务器断开连接")
                }
            }
        }
    }

    private fun handle(msg: ServerMessage) {
        when (msg) {
            is ServerMessage.Joined -> {
                mySeat = msg.mySeat
                roomCode = msg.roomCode
            }
            is ServerMessage.Lobby -> {
                hostSeat = msg.hostSeat
                roomCode = msg.roomCode
                _phase.value = Phase.InLobby(msg.roomCode, msg.seats, msg.hostSeat, msg.seatCount, mySeat, msg.aiFill)
            }
            is ServerMessage.Update -> {
                _uiState.value = GameUiState(
                    table = msg.view,
                    lastEvents = msg.events,
                    online = true,
                    isHost = mySeat == hostSeat,
                    roomCode = roomCode
                )
                _phase.value = Phase.Playing
            }
            is ServerMessage.Error -> {
                if (msg.code in setOf("join_failed", "not_found", "bad_seat_count")) {
                    _phase.value = Phase.Ended(msg.message)
                } else {
                    _error.value = msg.message
                }
            }
            ServerMessage.RoomClosed -> _phase.value = Phase.Ended("房间已关闭")
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun setAiFill(enabled: Boolean) {
        scope.launch { runCatching { client.send(ClientMessage.SetAiFill(enabled)) } }
    }

    override fun act(action: com.aniapp.pokerdemo.engine.PlayerAction) {
        if (!_uiState.value.isPlayerTurn) return
        scope.launch { runCatching { client.send(ClientMessage.Act(action)) } }
    }

    override fun nextHand() {
        scope.launch { runCatching { client.send(ClientMessage.StartHand) } }
    }

    override fun newMatch() {
        nextHand()
    }

    override fun dispose() {
        disposed = true
        cleanupScope.launch { runCatching { client.close() } }
    }
}