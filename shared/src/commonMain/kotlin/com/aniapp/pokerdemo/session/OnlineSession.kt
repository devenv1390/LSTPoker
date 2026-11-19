package com.aniapp.pokerdemo.session

import com.aniapp.pokerdemo.engine.ai.AiDifficulty
import com.aniapp.pokerdemo.net.ClientMessage
import com.aniapp.pokerdemo.net.GameClient
import com.aniapp.pokerdemo.net.LobbySeat
import com.aniapp.pokerdemo.net.PublicAddress
import com.aniapp.pokerdemo.net.ServerAddress
import com.aniapp.pokerdemo.net.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class OnlineConfig(
    val host: String,
    val port: Int,
    val nickname: String,
    val seatCount: Int? = null,
    val joinCode: String? = null,
    val aiFill: Boolean = true,
    val secure: Boolean = false,
    val autoDiscovery: Boolean = false,
    val difficulty: AiDifficulty = AiDifficulty.STANDARD
)

class OnlineSession(
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

    // 网络收发与 JSON 编解码离开主线程，避免点击与结算重组争抢 UI 帧
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = GameClient(scope)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var disposed = false
    private var mySeat = -1
    private var hostSeat = -1
    private var roomCode: String? = null
    private var pendingLobby: Phase.InLobby? = null

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
                withTimeout(30_000) {
                    // 免费隧道边缘偶发 502、DoH 缓存可能滞后：重试时换 DoH 源重新解析地址
                    var backoff = 1500L
                    repeat(3) { attempt ->
                        val target = if (attempt > 0 && config.autoDiscovery) {
                            PublicAddress.resolve(attempt) ?: config.toAddress()
                        } else {
                            config.toAddress()
                        }
                        try {
                            client.connect(target.host, target.port, target.secure)
                            return@withTimeout
                        } catch (e: Exception) {
                            if (attempt == 2) throw e
                            kotlinx.coroutines.delay(backoff)
                            backoff *= 2
                        }
                    }
                }
                val join = config.joinCode
                if (join != null) client.send(ClientMessage.JoinRoom(join, config.nickname))
                else client.send(ClientMessage.CreateRoom(config.nickname, config.seatCount ?: 6, config.aiFill, config.difficulty))
            } catch (e: Exception) {
                _phase.value = Phase.Ended(
                    if (config.secure) "无法连接服务器：请确认房主已启动服务器与隧道，且地址是最新的（快速隧道每次重启地址会变）"
                    else "无法连接服务器：请确认服务器已启动，地址与端口正确"
                )
            }
        }
        scope.launch {
            client.incoming.collect { handle(it) }
        }
        scope.launch {
            client.connected.drop(1).collect { connected ->
                if (!connected && !disposed && _phase.value !is Phase.Ended) {
                    _phase.value = Phase.Ended(
                        if (config.secure) "与服务器断开连接（房主机器或隧道可能已关闭）"
                        else "与服务器断开连接"
                    )
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
                val lobby = Phase.InLobby(msg.roomCode, msg.seats, msg.hostSeat, msg.seatCount, mySeat, msg.aiFill)
                // 手牌结束后服务器随即广播 Lobby，直接切换会跳过结算覆盖层，先挂起等玩家返回
                if (_phase.value == Phase.Playing && _uiState.value.table.result != null) {
                    pendingLobby = lobby
                } else {
                    pendingLobby = null
                    _phase.value = lobby
                }
            }
            is ServerMessage.Update -> {
                _uiState.value = GameUiState(
                    table = msg.view,
                    lastEvents = msg.events,
                    online = true,
                    isHost = mySeat == hostSeat,
                    roomCode = roomCode
                )
                if (msg.view.result == null) pendingLobby = null
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

    private fun OnlineConfig.toAddress() = ServerAddress(host, port, secure)

    fun backToLobby() {
        pendingLobby?.let {
            pendingLobby = null
            _phase.value = it
        }
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
        scope.cancel()
        cleanupScope.launch { runCatching { client.close() } }
    }
}