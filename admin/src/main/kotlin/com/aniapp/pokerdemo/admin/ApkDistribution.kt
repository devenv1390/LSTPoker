package com.aniapp.pokerdemo.admin

import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class ApkInfo(
    val fileName: String,
    val versionName: String,
    val versionCode: Int,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val sha256: String,
    val downloadUrl: String,
    val updateUrl: String
)

data class ApkRelease(val file: File, val info: ApkInfo)

/** 只暴露 app/build/outputs/apk/release 下由构建脚本生成的 Release APK。 */
class ApkDistribution(private val releaseDir: File) {
    private data class CacheEntry(val stamp: String, val info: ApkInfo)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun latest(): ApkRelease? {
        val dir = releaseDir.canonicalFile
        if (!dir.isDirectory) return null
        val file = dir.listFiles { candidate ->
            candidate.isFile && APK_NAME.matches(candidate.name) && runCatching {
                candidate.canonicalFile.parentFile == dir
            }.getOrDefault(false)
        }?.maxWithOrNull(
            compareBy<File> { versionCode(it.name) }
                .thenByDescending { it.lastModified() }
                .thenBy { it.name }
        ) ?: return null

        val stamp = "${file.lastModified()}|${file.length()}"
        val cached = cache[file.canonicalPath]
        val info = if (cached?.stamp == stamp) cached.info else {
            ApkInfo(
                fileName = file.name,
                versionName = versionName(file.name),
                versionCode = versionCode(file.name),
                sizeBytes = file.length(),
                modifiedAt = file.lastModified(),
                sha256 = sha256(file),
                downloadUrl = "/download/latest.apk",
                updateUrl = "/update"
            ).also { cache[file.canonicalPath] = CacheEntry(stamp, it) }
        }
        return ApkRelease(file, info)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun versionName(name: String): String =
        APK_NAME.matchEntire(name)?.groupValues?.get(1) ?: name.substringBefore("-release")

    private fun versionCode(name: String): Int =
        versionName(name).substringAfterLast('.').toIntOrNull() ?: 0

    private companion object {
        val APK_NAME = Regex("""LSTPoker-v(\d+\.\d+\.\d+)-release-\d{8}-\d{4}\.apk""")
    }
}
