package com.aniapp.pokerdemo.admin

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.head
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminApkRoutesTest {
    private suspend fun withRelease(block: suspend (java.io.File, String) -> Unit) {
        val dir = Files.createTempDirectory("poker-admin-routes").toFile()
        val name = "LSTPoker-v1.0.10-release-20261125-1300.apk"
        val bytes = "fake apk for route testing".toByteArray()
        try {
            dir.resolve(name).writeBytes(bytes)
            block(dir, name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `mobile update page and metadata are readable through lan host`() = testApplication {
        withRelease { dir, _ ->
            application { module(dir) }
            val page = client.get("/update") { header(HttpHeaders.Host, "192.168.3.18:8787") }
            assertEquals(HttpStatusCode.OK, page.status)
            assertTrue(page.bodyAsText().contains("LSTPoker"))

            val metadata = client.get("/api/apk") { header(HttpHeaders.Host, "192.168.3.18:8787") }
            assertEquals(HttpStatusCode.OK, metadata.status)
            assertTrue(metadata.bodyAsText().contains("LSTPoker-v1.0.10"))
        }
    }

    @Test
    fun `apk download has attachment mime length etag and conditional caching`() = testApplication {
        withRelease { dir, name ->
            application { module(dir) }
            val response = client.get("/download/latest.apk") {
                header(HttpHeaders.Host, "192.168.3.18:8787")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("application/vnd.android.package-archive", response.headers[HttpHeaders.ContentType]?.substringBefore(';'))
            assertNotNull(response.headers[HttpHeaders.ContentLength])
            assertEquals("attachment; filename=\"$name\"", response.headers[HttpHeaders.ContentDisposition])
            assertEquals("nosniff", response.headers["X-Content-Type-Options"])
            assertTrue(response.headers[HttpHeaders.ETag]!!.startsWith("\""))

            val etag = response.headers[HttpHeaders.ETag]!!
            val notModified = client.get("/download/latest.apk") {
                header(HttpHeaders.Host, "192.168.3.18:8787")
                header(HttpHeaders.IfNoneMatch, etag)
            }
            assertEquals(HttpStatusCode.NotModified, notModified.status)
        }
    }

    @Test
    fun `head request exposes download headers without body`() = testApplication {
        withRelease { dir, _ ->
            application { module(dir) }
            val response = client.head("/download/latest.apk") {
                header(HttpHeaders.Host, "192.168.3.18:8787")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertNotNull(response.headers[HttpHeaders.ContentDisposition])
            assertNotNull(response.headers[HttpHeaders.ContentLength])
        }
    }

    @Test
    fun `lan access cannot use management routes or write operations`() = testApplication {
        withRelease { dir, _ ->
            application { module(dir) }
            val status = client.get("/api/status") { header(HttpHeaders.Host, "192.168.3.18:8787") }
            assertEquals(HttpStatusCode.Forbidden, status.status)
            val root = client.get("/") { header(HttpHeaders.Host, "192.168.3.18:8787") }
            assertEquals(HttpStatusCode.Forbidden, root.status)
            val write = client.post("/api/service/stop") {
                header(HttpHeaders.Host, "192.168.3.18:8787")
                header("X-Admin-Request", "1")
            }
            assertEquals(HttpStatusCode.Forbidden, write.status)
            val external = client.get("/update") { header(HttpHeaders.Host, "example.com:8787") }
            assertEquals(HttpStatusCode.Forbidden, external.status)
        }
    }

    @Test
    fun `missing release returns not found for public endpoints`() = testApplication {
        val dir = Files.createTempDirectory("poker-admin-empty").toFile()
        try {
            application { module(dir) }
            val response = client.get("/api/apk") { header(HttpHeaders.Host, "192.168.3.18:8787") }
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertTrue(response.bodyAsText().contains("尚未生成 Release APK"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
