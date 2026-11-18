package com.aniapp.pokerdemo.server

import com.aniapp.pokerdemo.engine.GameEvent
import com.aniapp.pokerdemo.engine.Phase
import com.aniapp.pokerdemo.engine.PlayerAction
import com.aniapp.pokerdemo.engine.PlayerInit
import com.aniapp.pokerdemo.engine.TableEngine
import com.aniapp.pokerdemo.engine.LegalActions
import com.aniapp.pokerdemo.engine.ai.AiStrategy
import com.aniapp.pokerdemo.engine.ai.AiView
import com.aniapp.pokerdemo.engine.ai.aiViewOf
import com.aniapp.pokerdemo.engine.ai.sanitize
import com.aniapp.pokerdemo.engine.ai.mc.MonteCarloAi
import com.aniapp.pokerdemo.engine.ai.mc.MonteCarloConfig
import com.aniapp.pokerdemo.net.LobbySeat
import com.aniapp.pokerdemo.net.PokerJson
import com.aniapp.pokerdemo.net.SeatInfo
import com.aniapp.pokerdemo.net.ServerMessage
import com.aniapp.pokerdemo.net.TableView
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class RoomException(message: String) : Exception(message)

private class SeatHolder(
    val name: String,
    var session: WebSocketServerSession?,
    var isAi: Boolean,
    var connected: Boolean
)

class RoomManager(private val aiDelayMs: LongRange = 700L..1400L) {
    private val rooms = ConcurrentHashMap<String, Room>()
    private val random = Random.Default

    fun create(seatCount: Int, aiFill: Boolean = true): Room {
        require(seatCount in 2..6)
        var code: String
        do {
            code = (1..4).map { ('A'..'Z').random(random) }.joinToString("")
        } while (rooms.containsKey(code))
        val room = Room(code, seatCount, this, aiDelayMs, random, aiFill)
        rooms[code] = room
        return room
    }

    fun get(code: String): Room? = rooms[code.trim().uppercase()]

    fun remove(code: String) {
        rooms.remove(code)
    }

    suspend fun snapshotAll(): List<RoomSnapshot> =
        rooms.values.sortedBy { it.code }.map { it.snapshot() }
}

@Serializable
data class SeatSnap(val seat: Int, val name: String, val isAi: Boolean, val connected: Boolean)

@Serializable
data class HandSnap(val handNumber: Int, val phase: String, val pot: Int)

@Serializable
data class RoomSnapshot(
    val code: String,
    val seatCount: Int,
    val aiFill: Boolean,
    val hostSeat: Int,
    val humans: Int,
    val ais: Int,
    val inHand: Boolean,
    val seats: List<SeatSnap>,
    val hand: HandSnap?
)

class Room(
    val code: String,
    val seatCount: Int,
    private val manager: RoomManager,
    private val aiDelayMs: LongRange,
    private val random: Random,
    var aiFill: Boolean = true,
    private val ai: AiStrategy = MonteCarloAi(MonteCarloConfig.Server)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val seats = arrayOfNulls<SeatHolder>(seatCount)
    private var hostSeat = -1
    private var engine: TableEngine? = null
    // 引擎座位连续编号；关闭 AI 补位时房间座位可能稀疏，此表把引擎索引映射回房间座位
    private var seatMap: List<Int> = emptyList()
    private var inHand = false
    private var aiJob: Job? = null

    private val humanCount: Int get() = seats.count { it != null && !it.isAi }

    suspend fun snapshot(): RoomSnapshot = mutex.withLock {
        val e = engine
        RoomSnapshot(
            code = code,
            seatCount = seatCount,
            aiFill = aiFill,
            hostSeat = hostSeat,
            humans = humanCount,
            ais = seats.count { it != null && it.isAi },
            inHand = inHand,
            seats = seats.mapIndexedNotNull { i, h -> h?.let { SeatSnap(i, it.name, it.isAi, it.connected) } },
            hand = if (inHand && e != null) HandSnap(e.state.handNumber, e.state.phase.name, e.state.pot) else null
        )
    }

    suspend fun addHuman(nickname: String, session: WebSocketServerSession): Int = mutex.withLock {
        if (inHand) throw RoomException("对局进行中，无法加入")
        val seat = seats.indexOfFirst { it == null }
        if (seat < 0) throw RoomException("房间已满")
        seats[seat] = SeatHolder(nickname, session, isAi = false, connected = true)
        if (hostSeat < 0 || seats[hostSeat] == null) hostSeat = seat
        seat
    }

    suspend fun requestStart(seat: Int) {
        mutex.withLock {
            if (inHand) return sendError(seat, "already_started", "对局已开始")
            if (seat != hostSeat) return sendError(seat, "not_host", "只有房主能开始游戏")
            if (aiFill) {
                for (i in seats.indices) {
                    if (seats[i] == null) seats[i] = SeatHolder("AI-${i + 1}", null, isAi = true, connected = true)
                }
            } else if (humanCount < 2) {
                return sendError(seat, "need_players", "已关闭 AI 补位，至少需要 2 名玩家才能开始")
            }
            val occupied = seats.indices.filter { seats[it] != null }
            val names = occupied.map { seats[it]!!.name }
            val existing = engine
            val e = if (existing != null && existing.state.players.map { it.name } == names) {
                if (existing.state.phase == Phase.MATCH_OVER) existing.startMatch()
                existing
            } else {
                TableEngine(players = names.map { PlayerInit(it) }, random = random)
            }
            engine = e
            seatMap = occupied
            inHand = true
            val events = e.startHand()
            broadcastUpdate(events)
            maybeAiLoop()
        }
    }

    suspend fun requestAct(seat: Int, action: PlayerAction) {
        mutex.withLock {
            val e = engine
            if (!inHand || e == null) return sendError(seat, "not_in_hand", "当前没有进行中的对局")
            val eSeat = seatMap.indexOf(seat)
            if (eSeat < 0) return sendError(seat, "not_in_hand", "当前没有进行中的对局")
            try {
                val events = e.act(eSeat, action)
                broadcastUpdate(events)
                if (!e.state.phase.isBetting()) endHand() else maybeAiLoop()
            } catch (ex: IllegalStateException) {
                sendError(seat, "illegal_action", ex.message ?: "非法动作")
            }
        }
    }

    suspend fun setAiFill(seat: Int, enabled: Boolean) {
        mutex.withLock {
            if (seat != hostSeat) return sendError(seat, "not_host", "只有房主能修改 AI 补位")
            if (inHand) return sendError(seat, "already_started", "对局进行中，无法修改 AI 补位")
            aiFill = enabled
            broadcastLobby()
        }
    }

    suspend fun disconnected(seat: Int) {
        mutex.withLock {
            val holder = seats[seat] ?: return
            if (!inHand) {
                seats[seat] = null
                if (hostSeat == seat) hostSeat = seats.indexOfFirst { it != null && !it.isAi }
                if (humanCount == 0) {
                    close()
                    return
                }
                broadcastLobby()
            } else {
                holder.connected = false
                holder.isAi = true
                holder.session = null
                val e = engine
                if (e != null && e.state.phase.isBetting() && e.state.acting == seatMap.indexOf(seat)) maybeAiLoop()
            }
        }
    }

    suspend fun broadcastLobby() {
        val lobby = ServerMessage.Lobby(
            roomCode = code,
            seats = seats.mapIndexed { i, h ->
                if (h == null) null else LobbySeat(i, h.name, h.isAi, h.connected)
            }.filterNotNull(),
            hostSeat = hostSeat,
            seatCount = seatCount,
            aiFill = aiFill
        )
        sendToHumans(lobby)
    }

    // AI 回合快照：在锁内采集，供锁外做蒙特卡洛决策后校验提交
    private class AiSnapshot(
        val engineSeat: Int,
        val view: AiView,
        val legal: LegalActions,
        val fingerprint: List<Int>
    )

    private enum class AiStep { CONTINUE_AI, STOP, STALE, DONE }

    /** 须在持有 mutex 时调用；当前行动者不是 AI 或无进行中对局时返回 null */
    private fun snapshotAiTurn(): AiSnapshot? {
        val eng = engine ?: return null
        if (!inHand || !eng.state.phase.isBetting()) return null
        val s = eng.state.acting ?: return null
        val sRoom = seatMap.getOrNull(s) ?: return null
        if (seats[sRoom]?.isAi != true) return null
        val legal = eng.legalActions(s)
        return AiSnapshot(
            engineSeat = s,
            view = aiViewOf(eng.state, s, legal.callAmount, eng.config.bigBlind),
            legal = legal,
            fingerprint = fingerprintOf(eng)
        )
    }

    private fun fingerprintOf(eng: TableEngine): List<Int> = with(eng.state) {
        listOf(handNumber, phase.ordinal, acting ?: -1, currentBet, minRaiseTo, pot) +
            players.flatMap { listOf(it.streetBet, it.chips, if (it.isFolded) 1 else 0, if (it.isAllIn) 1 else 0) }
    }

    private fun maybeAiLoop() {
        val e = engine ?: return
        if (!e.state.phase.isBetting()) return
        val acting = e.state.acting ?: return
        val actingRoom = seatMap.getOrNull(acting) ?: return
        if (seats[actingRoom]?.isAi != true) return
        if (aiJob?.isActive == true) return
        aiJob = scope.launch {
            while (true) {
                delay(random.nextLong(aiDelayMs.first, aiDelayMs.last + 1))
                var staleRetries = 0
                while (true) {
                    val snap = mutex.withLock { snapshotAiTurn() } ?: return@launch
                    // 重计算放在锁外，人类动作不被 AI 思考阻塞
                    val action = try {
                        sanitize(ai.decide(snap.view, snap.legal, random), snap.legal)
                    } catch (_: Exception) {
                        when {
                            snap.legal.canCall -> PlayerAction.Call
                            snap.legal.canCheck -> PlayerAction.Check
                            else -> PlayerAction.Fold
                        }
                    }
                    val step = mutex.withLock {
                        val eng = engine ?: return@withLock AiStep.DONE
                        if (!inHand) return@withLock AiStep.DONE
                        if (fingerprintOf(eng) != snap.fingerprint) {
                            // 状态已被其他路径推进；该路径会自行重启 AI 循环或交接人类
                            return@withLock if (eng.state.phase.isBetting()) AiStep.STALE else AiStep.DONE
                        }
                        val events = eng.act(snap.engineSeat, action)
                        broadcastUpdate(events)
                        if (!eng.state.phase.isBetting()) {
                            endHand()
                            AiStep.STOP
                        } else {
                            val next = eng.state.acting
                            val nextRoom = next?.let { seatMap.getOrNull(it) }
                            if (next == null || nextRoom == null || seats[nextRoom]?.isAi != true) {
                                AiStep.STOP
                            } else {
                                AiStep.CONTINUE_AI
                            }
                        }
                    }
                    when (step) {
                        AiStep.CONTINUE_AI -> {
                            staleRetries = 0
                        }
                        AiStep.STOP, AiStep.DONE -> return@launch
                        AiStep.STALE -> {
                            if (++staleRetries >= 8) return@launch
                        }
                    }
                }
            }
        }
    }

    private suspend fun endHand() {
        inHand = false
        for (i in seats.indices) {
            val h = seats[i]
            if (h != null && (h.isAi || !h.connected)) seats[i] = null
        }
        if (hostSeat < 0 || seats[hostSeat] == null) hostSeat = seats.indexOfFirst { it != null }
        if (humanCount == 0) {
            close()
            return
        }
        broadcastLobby()
    }

    private suspend fun close() {
        sendToHumans(ServerMessage.RoomClosed)
        scope.cancel()
        manager.remove(code)
    }

    private suspend fun broadcastUpdate(events: List<GameEvent>) {
        val e = engine ?: return
        seats.forEachIndexed { i, h ->
            if (h == null || h.isAi || !h.connected) return@forEachIndexed
            val session = h.session ?: return@forEachIndexed
            val msg = ServerMessage.Update(viewFor(e, i), events)
            try {
                session.send(Frame.Text(PokerJson.encodeToString<ServerMessage>(msg)))
            } catch (_: Exception) {
                h.connected = false
                h.isAi = true
                h.session = null
            }
        }
    }

    private fun viewFor(e: TableEngine, seat: Int): TableView {
        val s = e.state
        val eSeat = seatMap.indexOf(seat)
        val revealed = if (s.showdown) {
            s.players.filter { !it.isFolded }.associate { seatMap[it.id] to it.hole }
        } else emptyMap()
        return TableView(
            seats = s.players.map { p ->
                val roomSeat = seatMap[p.id]
                val h = seats[roomSeat]
                SeatInfo(
                    seat = roomSeat,
                    name = p.name,
                    chips = p.chips,
                    streetBet = p.streetBet,
                    totalCommitted = p.totalCommitted,
                    folded = p.isFolded,
                    allIn = p.isAllIn,
                    connected = h?.connected ?: false,
                    isAi = h?.isAi ?: true
                )
            },
            board = s.board,
            dealer = seatMap[s.dealer],
            acting = s.acting?.let { seatMap[it] },
            phase = s.phase,
            handNumber = s.handNumber,
            currentBet = s.currentBet,
            minRaiseTo = s.minRaiseTo,
            pot = s.pot,
            mySeat = seat,
            myHole = s.of(eSeat).hole,
            legal = e.legalActions(eSeat),
            result = s.result?.let { r ->
                r.copy(
                    awards = r.awards.map { a -> a.copy(winners = a.winners.map { seatMap[it] }) },
                    revealed = r.revealed.mapKeys { seatMap[it.key] }
                )
            },
            revealedHoles = revealed
        )
    }

    private suspend fun sendToHumans(msg: ServerMessage) {
        val json = PokerJson.encodeToString<ServerMessage>(msg)
        seats.forEach { h ->
            if (h == null || h.isAi || !h.connected) return@forEach
            val session = h.session ?: return@forEach
            try {
                // Ktor 发送后会消费帧内容，同一 Frame 实例不能复用给多个会话
                session.send(Frame.Text(json))
            } catch (_: Exception) {
                h.connected = false
            }
        }
    }

    private suspend fun sendError(seat: Int, code: String, message: String) {
        val session = seats[seat]?.session ?: return
        try {
            session.send(Frame.Text(PokerJson.encodeToString<ServerMessage>(ServerMessage.Error(code, message))))
        } catch (_: Exception) {
        }
    }
}