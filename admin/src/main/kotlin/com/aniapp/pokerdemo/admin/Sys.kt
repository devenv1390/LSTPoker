package com.aniapp.pokerdemo.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class CmdResult(val exit: Int, val out: String)

fun runCmd(vararg cmd: String, timeoutSec: Long = 15): CmdResult {
    val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
        p.destroyForcibly()
        return CmdResult(-1, out)
    }
    return CmdResult(p.exitValue(), out)
}

private fun now() = System.currentTimeMillis()

@Serializable
data class PublicProbe(val ok: Boolean, val code: Int?, val latencyMs: Long?, val checkedAt: Long)

@Serializable
data class ServiceDto(val loaded: Boolean, val state: String?, val daemonPid: Int?)

@Serializable
data class ServerDto(
    val up: Boolean,
    val pid: Int?,
    val uptimeSec: Long?,
    val rssKb: Long?,
    val probeMs: Long?,
    val probeCode: Int?
)

@Serializable
data class TunnelDto(val pid: Int?, val uptimeSec: Long?, val mode: String)

@Serializable
data class OpDto(val kind: String, val phase: String, val startedAt: Long, val lines: List<String>)

@Serializable
data class OpResult(val kind: String, val ok: Boolean, val message: String, val finishedAt: Long)

@Serializable
data class AdminStatus(
    val service: ServiceDto,
    val server: ServerDto,
    val tunnel: TunnelDto,
    val public: PublicProbe,
    val address: String?,
    val op: OpDto?,
    val lastResult: OpResult?,
    val game: JsonElement?
)

object Sys {
    val home: String = System.getProperty("user.home")
    val uid: String = runCmd("id", "-u").out.trim()
    val root: File = findRoot()
    val stateDir = File(home, ".config/poker-daemon")
    private const val LABEL = "com.aniapp.pokerdemo.server"
    private val plist = File(home, "Library/LaunchAgents/$LABEL.plist")
    private val target = "gui/$uid/$LABEL"
    private val healthyCodes = setOf(400, 426, 101)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun findRoot(): File {
        var d: File? = File(Sys::class.java.protectionDomain.codeSource.location.toURI()).parentFile
        while (d != null) {
            if (File(d, "settings.gradle").exists()) return d
            d = d.parentFile
        }
        return File(System.getProperty("user.dir"))
    }

    data class ServiceInfo(val loaded: Boolean, val state: String?, val pid: Int?)

    fun serviceInfo(): ServiceInfo {
        val r = runCmd("launchctl", "print", target)
        if (r.exit != 0) return ServiceInfo(false, null, null)
        val state = Regex("""state = (\w+)""").find(r.out)?.groupValues?.get(1)
        val pid = Regex("""(?m)^\s*pid = (\d+)""").find(r.out)?.groupValues?.get(1)?.toIntOrNull()
        return ServiceInfo(true, state, pid)
    }

    fun serverPid(): Int? =
        runCmd("lsof", "-nP", "-t", "-iTCP:8080", "-sTCP:LISTEN")
            .out.trim().lineSequence().firstOrNull()?.toIntOrNull()

    fun tunnelPid(): Int? =
        runCmd("pgrep", "-f", "cloudflared tunnel --no-autoupdate")
            .out.trim().lineSequence().firstOrNull()?.toIntOrNull()

    fun psInfo(pid: Int): Pair<Long, Long>? {
        val r = runCmd("ps", "-o", "etime=,rss=", "-p", pid.toString())
        if (r.exit != 0) return null
        val parts = r.out.trim().split(Regex("\\s+"))
        if (parts.size < 2) return null
        return parseEtime(parts[0]) to (parts[1].toLongOrNull() ?: 0L)
    }

    private fun parseEtime(s: String): Long {
        var days = 0L
        var rest = s
        if ('-' in s) {
            days = s.substringBefore('-').toLongOrNull() ?: 0L
            rest = s.substringAfter('-')
        }
        val nums = rest.split(':').map { it.toLongOrNull() ?: 0L }
        val sec = when (nums.size) {
            3 -> nums[0] * 3600 + nums[1] * 60 + nums[2]
            2 -> nums[0] * 60 + nums[1]
            else -> nums.getOrElse(0) { 0L }
        }
        return days * 86400 + sec
    }

    fun probe(url: String, timeoutMs: Int): Pair<Int, Long>? {
        val t0 = now()
        return try {
            val c = URI(url).toURL().openConnection() as HttpURLConnection
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = false
            val code = c.responseCode
            c.disconnect()
            code to (now() - t0)
        } catch (_: Exception) {
            null
        }
    }

    fun localProbe(): Pair<Int, Long>? = probe("http://127.0.0.1:8080/ws", 1500)

    fun serverHealthy(): Boolean = localProbe()?.first in healthyCodes

    @Volatile
    var publicCache: PublicProbe = PublicProbe(false, null, null, 0)
        private set

    fun startPublicProbeLoop() {
        scope.launch {
            while (true) {
                val r = probe("https://poker.lain-priv.work/ws", 8000)
                publicCache = PublicProbe(
                    r != null && r.first in healthyCodes,
                    r?.first, r?.second, now()
                )
                delay(20_000)
            }
        }
    }

    fun gameStatus(): JsonElement? = try {
        val c = URI("http://127.0.0.1:8080/status").toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 1000
        c.readTimeout = 1000
        if (c.responseCode != 200) {
            c.disconnect()
            null
        } else {
            val body = c.inputStream.bufferedReader().readText()
            c.disconnect()
            Json.parseToJsonElement(body)
        }
    } catch (_: Exception) {
        null
    }

    fun address(): String? =
        File(stateDir, "address").takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }

    fun tunnelMode(): String = try {
        if (plist.readText().contains("<string>lhr</string>")) "lhr" else "cf"
    } catch (_: Exception) {
        "cf"
    }

    fun buildStatus(): AdminStatus {
        val svc = serviceInfo()
        val sp = serverPid()
        val lp = localProbe()
        val sps = sp?.let { psInfo(it) }
        val tp = tunnelPid()
        val tps = tp?.let { psInfo(it) }
        val up = lp != null && lp.first in healthyCodes
        return AdminStatus(
            service = ServiceDto(svc.loaded, svc.state, svc.pid),
            server = ServerDto(up, sp, sps?.first, sps?.second, lp?.second, lp?.first),
            tunnel = TunnelDto(tp, tps?.first, tunnelMode()),
            public = publicCache,
            address = address(),
            op = opDto(),
            lastResult = lastResult,
            game = if (up) gameStatus() else null
        )
    }

    private class RunningOp(val kind: String, val startedAt: Long) {
        @Volatile
        var phase: String = "准备中"
        private val lines = mutableListOf<String>()
        fun line(s: String) {
            synchronized(lines) {
                lines.add(s)
                while (lines.size > 400) lines.removeAt(0)
            }
        }

        fun dto() = OpDto(kind, phase, startedAt, synchronized(lines) { lines.toList() })
    }

    private val currentOp = AtomicReference<RunningOp?>(null)

    @Volatile
    var lastResult: OpResult? = null
        private set

    fun opDto(): OpDto? = currentOp.get()?.dto()

    fun startOp(kind: String): Boolean {
        val op = RunningOp(kind, now())
        if (!currentOp.compareAndSet(null, op)) return false
        scope.launch {
            val result = try {
                when (kind) {
                    "start" -> doStart(op)
                    "stop" -> doStop(op)
                    "restart" -> doRestart(op)
                    "rebuild" -> doRebuild(op)
                    "tunnel" -> doTunnel(op)
                    else -> OpResult(kind, false, "未知操作", now())
                }
            } catch (ex: Exception) {
                OpResult(kind, false, "操作异常：${ex.message}", now())
            }
            op.line(if (result.ok) "✓ ${result.message}" else "✗ ${result.message}")
            lastResult = result
            currentOp.set(null)
        }
        return true
    }

    private suspend fun waitHealthy(op: RunningOp, sec: Int): Boolean {
        val deadline = now() + sec * 1000L
        while (now() < deadline) {
            if (serverHealthy()) {
                op.line("8080 健康检查通过")
                return true
            }
            delay(1000)
        }
        return false
    }

    private suspend fun doStop(op: RunningOp): OpResult {
        op.phase = "停止中"
        if (serviceInfo().loaded) {
            op.line("launchctl bootout $target")
            val r = runCmd("launchctl", "bootout", target, timeoutSec = 30)
            op.line("bootout exit=${r.exit}")
        } else {
            op.line("服务未加载，无需停止")
        }
        val deadline = now() + 15_000
        while (now() < deadline) {
            if (serverPid() == null && tunnelPid() == null && !serviceInfo().loaded) break
            delay(500)
        }
        serverPid()?.let {
            op.line("清理残留服务器进程 pid=$it")
            runCmd("kill", "-9", it.toString())
        }
        tunnelPid()?.let {
            op.line("清理残留隧道进程 pid=$it")
            runCmd("kill", "-9", it.toString())
        }
        delay(500)
        val ok = !serviceInfo().loaded && serverPid() == null && tunnelPid() == null
        return OpResult("stop", ok, if (ok) "服务已停止，公网入口已关闭" else "停止后仍有残留，请查看日志", now())
    }

    private suspend fun doStart(op: RunningOp): OpResult {
        op.phase = "启动中"
        if (serviceInfo().loaded) {
            op.line("服务已加载，等待服务器就绪")
        } else {
            op.line("launchctl bootstrap gui/$uid ${plist.path}")
            val r = runCmd("launchctl", "bootstrap", "gui/$uid", plist.path, timeoutSec = 30)
            op.line("bootstrap exit=${r.exit}")
            if (r.exit != 0 && !serviceInfo().loaded) {
                return OpResult("start", false, "bootstrap 失败：${r.out.takeLast(300)}", now())
            }
        }
        op.phase = "等待服务器监听 8080"
        val ok = waitHealthy(op, 90)
        return OpResult("start", ok, if (ok) "服务器已启动并监听 8080" else "90 秒内未就绪，请查看日志", now())
    }

    private suspend fun doRestart(op: RunningOp): OpResult {
        op.phase = "重启中"
        if (!serviceInfo().loaded) return doStart(op).copy(kind = "restart")
        op.line("launchctl kickstart -k $target")
        val r = runCmd("launchctl", "kickstart", "-k", target, timeoutSec = 30)
        op.line("kickstart exit=${r.exit}")
        if (r.exit != 0 && !serviceInfo().loaded) {
            return OpResult("restart", false, "kickstart 失败：${r.out.takeLast(300)}", now())
        }
        op.phase = "等待服务器恢复"
        val ok = waitHealthy(op, 90)
        return OpResult("restart", ok, if (ok) "服务已重启" else "90 秒内未恢复，请查看日志", now())
    }

    private suspend fun doRebuild(op: RunningOp): OpResult {
        op.phase = "停止服务"
        val stop = doStop(op)
        if (!stop.ok) return stop.copy(kind = "rebuild")
        op.phase = "构建中（:server:installDist）"
        val p = ProcessBuilder("./gradlew", ":server:installDist", "--console=plain")
            .directory(root).redirectErrorStream(true).start()
        val reader = p.inputStream.bufferedReader()
        while (true) {
            val l = reader.readLine() ?: break
            op.line(l)
        }
        if (!p.waitFor(600, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            return OpResult("rebuild", false, "构建超时（10 分钟），服务保持停止", now())
        }
        if (p.exitValue() != 0) {
            return OpResult("rebuild", false, "构建失败（exit ${p.exitValue()}），服务保持停止", now())
        }
        op.line("构建完成")
        op.phase = "启动服务"
        val start = doStart(op)
        return start.copy(
            kind = "rebuild",
            message = if (start.ok) "重建完成，服务器已启动" else start.message
        )
    }

    private suspend fun doTunnel(op: RunningOp): OpResult {
        op.phase = "重启隧道"
        if (!serviceInfo().loaded) {
            return OpResult("tunnel", false, "服务未运行，无法单独重启隧道", now())
        }
        val old = tunnelPid() ?: return OpResult("tunnel", false, "未找到 cloudflared 进程", now())
        op.line("SIGTERM cloudflared pid=$old，守护脚本将在 5 秒内自动拉起")
        runCmd("kill", "-TERM", old.toString())
        val deadline = now() + 20_000
        while (now() < deadline) {
            val p = tunnelPid()
            if (p != null && p != old) {
                op.line("新隧道进程 pid=$p")
                return OpResult("tunnel", true, "隧道已重启（pid $p）", now())
            }
            delay(500)
        }
        return OpResult("tunnel", false, "20 秒内未见新隧道进程，请查看日志", now())
    }
}