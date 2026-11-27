package com.aniapp.pokerdemo.update

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateDownloadTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `accepts cached apk only when size and hash match`() {
        val file = folder.newFile("release.apk").apply { writeBytes("verified apk".toByteArray()) }
        val metadata = metadata("verified apk".toByteArray())
        val repository = UpdateRepository(folder.root)
        try {
            assertTrue(repository.validateCached(file, metadata))
            assertFalse(repository.validateCached(file, metadata.copy(sizeBytes = file.length() + 1)))
            assertFalse(repository.validateCached(file, metadata.copy(sha256 = "0".repeat(64))))
        } finally {
            repository.close()
        }
    }

    @Test
    fun `rejects missing and empty cache files`() {
        val repository = UpdateRepository(folder.root)
        try {
            assertFalse(repository.validateCached(File(folder.root, "missing.apk"), metadata(byteArrayOf(1))))
            assertFalse(repository.validateCached(folder.newFile("empty.apk"), metadata(byteArrayOf(1))))
        } finally {
            repository.close()
        }
    }

    private fun metadata(bytes: ByteArray) = UpdateMetadata(
        fileName = "LSTPoker-v1.0.13000-release-20261127-1200.apk",
        versionName = "1.0.13000",
        versionCode = 13000,
        sizeBytes = bytes.size.toLong(),
        modifiedAt = 1795606705408,
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    )
}
