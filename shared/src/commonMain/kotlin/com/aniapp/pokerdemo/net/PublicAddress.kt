package com.aniapp.pokerdemo.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object PublicAddress {
    private const val NAME = "_poker.lain-priv.work"
    private val PROVIDERS = listOf(
        "https://dns.alidns.com/resolve?name=$NAME&type=TXT",
        "https://doh.pub/dns-query?name=$NAME&type=TXT"
    )

    suspend fun resolve(startProvider: Int = 0): ServerAddress? {
        for (i in PROVIDERS.indices) {
            val url = PROVIDERS[(startProvider + i) % PROVIDERS.size]
            val addr = runCatching {
                withTimeout(8_000) {
                    val client = HttpClient(CIO)
                    try {
                        val body = client.get(url) { headers.append("Accept", "application/dns-json") }.bodyAsText()
                        val txt = Json.parseToJsonElement(body).jsonObject["Answer"]
                            ?.jsonArray?.firstOrNull()?.jsonObject?.get("data")?.jsonPrimitive?.content
                        ServerAddress.parse(txt?.trim('"') ?: "")
                    } finally {
                        client.close()
                    }
                }
            }.getOrNull()
            if (addr != null) return addr
        }
        // DoH 全失败（污染/超时）时兜底固定域名；lhr 回退期间 TXT 仍优先，不受影响
        return ServerAddress.parse("wss://poker.lain-priv.work")
    }
}