package com.aniapp.pokerdemo.admin

import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

object LogTail {
    val files: Map<String, File> = mapOf(
        "daemon" to File(Sys.stateDir, "daemon.log"),
        "server" to File(Sys.stateDir, "server.log"),
        "cloudflared" to File(Sys.stateDir, "cloudflared.log")
    )

    fun tail(file: File, n: Int): List<String> {
        if (!file.exists()) return emptyList()
        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            val start = maxOf(0L, len - 64 * 1024)
            raf.seek(start)
            val buf = ByteArray((len - start).toInt())
            raf.readFully(buf)
            val lines = String(buf, Charsets.UTF_8).split('\n').filter { it.isNotEmpty() }
            return if (start > 0) lines.drop(1).takeLast(n) else lines.takeLast(n)
        }
    }

    suspend fun follow(file: File, initial: Int, send: suspend (String) -> Unit) {
        tail(file, initial).forEach { send(it) }
        var offset = if (file.exists()) file.length() else 0L
        var carry = ByteArray(0)
        var lastSend = System.currentTimeMillis()
        while (true) {
            try {
                if (file.exists()) {
                    val len = file.length()
                    if (len < offset) {
                        offset = 0
                        carry = ByteArray(0)
                        send("〔日志文件已重置〕")
                        lastSend = System.currentTimeMillis()
                    }
                    if (len > offset) {
                        RandomAccessFile(file, "r").use { raf ->
                            raf.seek(offset)
                            val buf = ByteArray(minOf(len - offset, 1L shl 20).toInt())
                            val read = raf.read(buf)
                            if (read > 0) {
                                offset += read
                                val chunk = carry + buf.copyOf(read)
                                var lastNl = -1
                                for (i in chunk.indices.reversed()) {
                                    if (chunk[i] == '\n'.code.toByte()) { lastNl = i; break }
                                }
                                if (lastNl >= 0) {
                                    String(chunk, 0, lastNl + 1, Charsets.UTF_8)
                                        .split('\n').filter { it.isNotEmpty() }
                                        .forEach { send(it) }
                                    carry = chunk.copyOfRange(lastNl + 1, chunk.size)
                                    lastSend = System.currentTimeMillis()
                                } else {
                                    carry = if (chunk.size > 1 shl 20) ByteArray(0) else chunk
                                }
                            }
                        }
                    }
                }
                if (System.currentTimeMillis() - lastSend > 15_000) {
                    send(":hb")
                    lastSend = System.currentTimeMillis()
                }
            } catch (_: IOException) {
                return
            }
            delay(1000)
        }
    }
}