package com.aniapp.pokerdemo.update

import com.aniapp.pokerdemo.net.PokerJson
import kotlinx.serialization.Serializable

@Serializable
data class UpdateMetadata(
    val fileName: String,
    val versionName: String,
    val versionCode: Int,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val sha256: String
) {
    fun validate(): UpdateMetadata {
        require(VERSION_NAME.matches(versionName)) { "版本号格式不合法" }
        require(versionCode == versionName.substringAfterLast('.').toIntOrNull()) { "版本号不一致" }
        require(fileName.matches(Regex("LSTPoker-v${Regex.escape(versionName)}-release-\\d{8}-\\d{4}\\.apk"))) {
            "安装包文件名不合法"
        }
        require(sizeBytes in 1..MAX_APK_BYTES) { "安装包大小不合法" }
        require(modifiedAt >= 0L) { "构建时间不合法" }
        require(SHA256.matches(sha256)) { "SHA-256 不合法" }
        return this
    }

    fun isNewerThan(currentVersionCode: Long): Boolean = versionCode.toLong() > currentVersionCode

    companion object {
        const val MAX_APK_BYTES = 512L * 1024L * 1024L
        const val MAX_METADATA_BYTES = 16 * 1024
        private val VERSION_NAME = Regex("""\d+\.\d+\.\d+""")
        private val SHA256 = Regex("""[0-9a-f]{64}""")

        fun parse(json: String): UpdateMetadata =
            PokerJson.decodeFromString<UpdateMetadata>(json).validate()
    }
}
