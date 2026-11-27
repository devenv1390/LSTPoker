package com.aniapp.pokerdemo.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateMetadataTest {
    @Test
    fun `parses valid release metadata`() {
        val metadata = UpdateMetadata.parse(
            """{"fileName":"LSTPoker-v1.0.13000-release-20261127-1200.apk","versionName":"1.0.13000","versionCode":13000,"sizeBytes":1234,"modifiedAt":1795606705408,"sha256":"${"a".repeat(64)}","downloadUrl":"https://evil.invalid/apk"}"""
        )
        assertTrue(metadata.isNewerThan(12999))
        assertFalse(metadata.isNewerThan(13000))
        assertFalse(metadata.isNewerThan(13001))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects filename that does not match version`() {
        metadata().copy(fileName = "LSTPoker-v9.9.9-release-20261127-1200.apk").validate()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects malformed sha`() {
        metadata().copy(sha256 = "not-a-hash").validate()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects oversized apk`() {
        metadata().copy(sizeBytes = UpdateMetadata.MAX_APK_BYTES + 1).validate()
    }

    private fun metadata() = UpdateMetadata(
        fileName = "LSTPoker-v1.0.13000-release-20261127-1200.apk",
        versionName = "1.0.13000",
        versionCode = 13000,
        sizeBytes = 1234,
        modifiedAt = 1795606705408,
        sha256 = "a".repeat(64)
    )
}
