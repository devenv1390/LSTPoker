package com.aniapp.pokerdemo.admin

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class OtaPushRequest(
    val campaignId: String,
    val targetVersionCode: Int,
    val targetVersionName: String,
    val sha256: String
)

@Serializable
data class OtaPushResult(
    val matched: Int,
    val sent: Int,
    val failed: Int,
    val otaConnections: Int
)

class OtaPublishException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 只能由本机管理面板调用；通过回环地址请求游戏服务器的 OTA 管理端点。 */
class OtaPublisher(
    private val endpoint: String = "http://127.0.0.1:8080/api/admin/v1/update-pushes",
    private val timeoutMillis: Long = 3_000
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun publish(release: ApkRelease): OtaPushResult {
        val request = OtaPushRequest(
            campaignId = UUID.randomUUID().toString(),
            targetVersionCode = release.info.versionCode,
            targetVersionName = release.info.versionName,
            sha256 = release.info.sha256
        )
        return try {
            HttpClient(CIO) {
                install(HttpTimeout) {
                    this.requestTimeoutMillis = timeoutMillis
                    connectTimeoutMillis = timeoutMillis
                    socketTimeoutMillis = timeoutMillis
                }
            }.use { client ->
                val response = client.post(endpoint) {
                    header("X-Admin-Request", "1")
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(request))
                }
                if (response.status != HttpStatusCode.OK) {
                    throw OtaPublishException("OTA 服务器返回 HTTP ${response.status.value}")
                }
                json.decodeFromString<OtaPushResult>(response.bodyAsText())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw OtaPublishException(failure.message ?: "无法连接 OTA 服务器", failure)
        }
    }
}
