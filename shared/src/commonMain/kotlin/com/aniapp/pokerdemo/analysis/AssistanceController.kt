package com.aniapp.pokerdemo.analysis

import com.aniapp.pokerdemo.engine.ai.mc.EquityResult
import com.aniapp.pokerdemo.net.TableView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 管理辅助分析的异步计算、局面指纹和会话内 equity 缓存。 */
class AssistanceController(
    private val analyzer: AssistanceAnalyzer = AssistanceAnalyzer(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxCacheSize: Int = 8
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<AssistanceState>(AssistanceState.Off)
    val state: StateFlow<AssistanceState> = _state.asStateFlow()

    private var enabled = false
    private var situation: AdviceSituation? = null
    private var generation = 0L
    private var calculation: Job? = null
    private val cache = object : LinkedHashMap<String, EquityResult>(maxCacheSize.coerceAtLeast(1), 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EquityResult>?): Boolean =
            size > maxCacheSize.coerceAtLeast(1)
    }

    suspend fun update(table: TableView, bigBlind: Int, enabled: Boolean) {
        val next = if (enabled) AdviceSituation.from(table, bigBlind) else null
        mutex.withLock {
            this.enabled = enabled
            if (!enabled || next == null) {
                generation++
                calculation?.cancel()
                calculation = null
                situation = null
                if (!enabled) cache.clear()
                _state.value = if (enabled) AssistanceState.Unavailable else AssistanceState.Off
                return
            }

            val previousSituation = situation
            situation = next
            val ready = _state.value as? AssistanceState.Ready
            if (ready?.analysis?.adviceKey == next.adviceKey) return

            val cached = cache[next.equityKey]
            if (cached != null) {
                generation++
                calculation?.cancel()
                calculation = null
                _state.value = AssistanceState.Ready(analyzer.analyze(next, cached))
                return
            }

            if (previousSituation?.equityKey == next.equityKey && calculation?.isActive == true) {
                _state.value = AssistanceState.Loading
                return
            }

            generation++
            calculation?.cancel()
            val token = generation
            _state.value = AssistanceState.Loading
            calculation = scope.launch { calculate(next, token) }
        }
    }

    private suspend fun calculate(snapshot: AdviceSituation, token: Long) {
        try {
            val context = currentCoroutineContext()
            val result = analyzer.calculateEquity(snapshot) { context.ensureActive() }
            mutex.withLock {
                if (!enabled || token != generation) return@withLock
                cache[snapshot.equityKey] = result
                val latest = situation
                if (latest?.equityKey == snapshot.equityKey) {
                    _state.value = AssistanceState.Ready(analyzer.analyze(latest, result))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutex.withLock {
                if (enabled && token == generation) _state.value = AssistanceState.Unavailable
            }
        }
    }

    fun dispose() {
        generation++
        calculation?.cancel()
        calculation = null
        situation = null
        enabled = false
        cache.clear()
        _state.value = AssistanceState.Off
        scope.cancel()
    }
}
