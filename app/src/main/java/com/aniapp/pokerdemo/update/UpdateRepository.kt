package com.aniapp.pokerdemo.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class UpdateRepository(private val cacheDir: File) {
    private val client = HttpClient(CIO) {
        followRedirects = false
        engine { requestTimeout = 180_000 }
    }

    suspend fun fetchMetadata(endpoint: UpdateEndpoint): UpdateMetadata = withTimeout(8_000) {
        val response = client.get(endpoint.metadataUrl)
        response.requireOk()
        val contentLength = response.contentLength()
        if (contentLength != null && contentLength > UpdateMetadata.MAX_METADATA_BYTES) {
            error("更新信息过大")
        }
        val bytes = readBounded(response, UpdateMetadata.MAX_METADATA_BYTES.toLong())
        UpdateMetadata.parse(bytes.toByteArray().toString(Charsets.UTF_8))
    }

    suspend fun download(
        endpoint: UpdateEndpoint,
        metadata: UpdateMetadata,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        cacheDir.mkdirs()
        val target = File(cacheDir, cachedName(metadata))
        if (validateCached(target, metadata)) return@withContext target
        val temporary = File(cacheDir, "${target.name}.part")
        temporary.delete()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            withTimeout(180_000) {
                client.prepareGet(endpoint.downloadUrl).execute { response ->
                    response.requireOk()
                    val contentLength = response.contentLength()
                    if (contentLength != null && contentLength != metadata.sizeBytes) {
                        error("安装包大小与更新信息不一致")
                    }
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    FileOutputStream(temporary).use { output ->
                        while (downloaded < metadata.sizeBytes) {
                            val count = channel.readAvailable(buffer)
                            if (count < 0) error("安装包下载不完整")
                            if (count == 0) continue
                            if (downloaded + count > metadata.sizeBytes) error("安装包大小超过预期")
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            downloaded += count
                            onProgress(downloaded, metadata.sizeBytes)
                        }
                    }
                }
            }
            if (downloaded != metadata.sizeBytes) error("安装包下载不完整")
            val actualSha = digest.digest().toHex()
            if (actualSha != metadata.sha256) error("安装包 SHA-256 校验失败")
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            target
        } finally {
            temporary.delete()
        }
    }

    fun validateCached(file: File, metadata: UpdateMetadata): Boolean =
        file.isFile && file.length() == metadata.sizeBytes && runCatching {
            sha256(file) == metadata.sha256
        }.getOrDefault(false)

    fun close() = client.close()

    private suspend fun readBounded(response: HttpResponse, maxBytes: Long): ByteArrayOutputStream {
        val output = ByteArrayOutputStream()
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(8192)
        while (true) {
            val count = channel.readAvailable(buffer)
            if (count < 0) break
            if (output.size() + count > maxBytes) error("更新信息过大")
            output.write(buffer, 0, count)
        }
        return output
    }

    private fun HttpResponse.requireOk() {
        if (status != HttpStatusCode.OK) error("更新服务响应异常（HTTP ${status.value}）")
    }

    private fun HttpResponse.contentLength(): Long? =
        headers[HttpHeaders.ContentLength]?.toLongOrNull()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun cachedName(metadata: UpdateMetadata): String =
        "LSTPoker-${metadata.versionCode}-${metadata.sha256.take(16)}.apk"
}
