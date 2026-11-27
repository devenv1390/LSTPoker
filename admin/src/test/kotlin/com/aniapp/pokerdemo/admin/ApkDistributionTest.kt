package com.aniapp.pokerdemo.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class ApkDistributionTest {
    @Test
    fun `selects newest release and reports verifiable metadata`() {
        val dir = Files.createTempDirectory("poker-apk-test").toFile()
        try {
            dir.resolve("LSTPoker-v1.0.9-release-20261125-1200.apk").writeText("old")
            val latest = dir.resolve("LSTPoker-v1.0.10-release-20261125-1300.apk")
            latest.writeBytes("latest apk".toByteArray())

            val release = ApkDistribution(dir).latest()!!
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(latest.readBytes())
                .joinToString("") { "%02x".format(it) }

            assertEquals(latest.name, release.info.fileName)
            assertEquals("1.0.10", release.info.versionName)
            assertEquals(10, release.info.versionCode)
            assertEquals(latest.length(), release.info.sizeBytes)
            assertEquals(digest, release.info.sha256)
            assertEquals("/download/latest.apk", release.info.downloadUrl)
            assertEquals("/update", release.info.updateUrl)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `ignores unrelated files and symlinks outside release directory`() {
        val parent = Files.createTempDirectory("poker-apk-parent").toFile()
        try {
            val dir = parent.resolve("release").apply { mkdirs() }
            dir.resolve("LSTPoker-v1.0.1-release-20261125-1200.apk").writeText("valid")
            dir.resolve("notes.txt").writeText("ignore")
            val outside = parent.resolve("outside.apk").apply { writeText("outside") }
            Files.createSymbolicLink(
                dir.resolve("LSTPoker-v9.9.9-release-20261125-1200.apk").toPath(),
                outside.toPath()
            )

            val release = ApkDistribution(dir).latest()
            assertEquals("LSTPoker-v1.0.1-release-20261125-1200.apk", release?.info?.fileName)
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun `refreshes hash when apk changes and returns null when absent`() {
        val dir = Files.createTempDirectory("poker-apk-cache").toFile()
        try {
            val distribution = ApkDistribution(dir)
            assertNull(distribution.latest())
            val apk = dir.resolve("LSTPoker-v1.0.1-release-20261125-1200.apk")
            apk.writeText("one")
            val first = distribution.latest()!!.info.sha256
            apk.writeText("two")
            apk.setLastModified(apk.lastModified() + 2_000)
            val second = distribution.latest()!!.info.sha256
            assertNotEquals(first, second)
        } finally {
            dir.deleteRecursively()
        }
    }
}
