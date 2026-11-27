package com.aniapp.pokerdemo.admin

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaAdminRoutesTest {
    private val json = Json { encodeDefaults = true }
    private val result = OtaPushResult(matched = 2, sent = 2, failed = 0, otaConnections = 2)

    private fun releaseDir(): File = Files.createTempDirectory("poker-admin-ota").toFile().apply {
        resolve("LSTPoker-v1.0.10-release-20261125-1300.apk").writeText("release apk")
    }

    private suspend fun withPublisherServer(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = json.encodeToString(result),
        block: suspend (String, AtomicReference<OtaPushRequest?>, AtomicReference<String?>) -> Unit
    ) {
        val received = AtomicReference<OtaPushRequest?>(null)
        val adminHeader = AtomicReference<String?>(null)
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(ContentNegotiation) { json() }
            routing {
                post("/api/admin/v1/update-pushes") {
                    adminHeader.set(call.request.headers["X-Admin-Request"])
                    received.set(call.receive<OtaPushRequest>())
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(status, responseBody)
                }
            }
        }.start(wait = false)
        try {
            val port = server.engine.resolvedConnectors().single().port
            block("http://127.0.0.1:$port/api/admin/v1/update-pushes", received, adminHeader)
        } finally {
            server.stop(0, 1_000)
        }
    }

    @Test
    fun `publisher sends campaign and release metadata and decodes result`() = runBlocking {
        val dir = releaseDir()
        try {
            withPublisherServer { endpoint, received, adminHeader ->
                val release = ApkDistribution(dir).latest()!!
                val published = OtaPublisher(endpoint).publish(release)
                assertEquals(result, published)
                val request = received.get()
                assertNotNull(request)
                assertEquals(UUID.fromString(request!!.campaignId).toString(), request.campaignId)
                assertEquals(10, request.targetVersionCode)
                assertEquals("1.0.10", request.targetVersionName)
                assertEquals(release.info.sha256, request.sha256)
                assertEquals("1", adminHeader.get())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `admin route returns not found without publishing when no apk exists`() = testApplication {
        val dir = Files.createTempDirectory("poker-admin-ota-empty").toFile()
        try {
            withPublisherServer { endpoint, received, _ ->
                application { module(dir, OtaPublisher(endpoint)) }
                val response = client.post("/api/update-pushes") {
                    header(HttpHeaders.Host, "127.0.0.1:8787")
                    header("X-Admin-Request", "1")
                }
                assertEquals(HttpStatusCode.NotFound, response.status)
                assertTrue(response.bodyAsText().contains("尚未生成 Release APK"))
                assertNull(received.get())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `admin route rejects lan forwarded and unauthenticated ota writes`() = testApplication {
        val dir = releaseDir()
        try {
            withPublisherServer { endpoint, received, _ ->
                application { module(dir, OtaPublisher(endpoint)) }
                val lan = client.post("/api/update-pushes") {
                    header(HttpHeaders.Host, "192.168.3.18:8787")
                    header("X-Admin-Request", "1")
                }
                assertEquals(HttpStatusCode.Forbidden, lan.status)
                val forwarded = client.post("/api/update-pushes") {
                    header(HttpHeaders.Host, "127.0.0.1:8787")
                    header("X-Admin-Request", "1")
                    header("X-Forwarded-For", "192.168.3.18")
                }
                assertEquals(HttpStatusCode.Forbidden, forwarded.status)
                val unauthenticated = client.post("/api/update-pushes") {
                    header(HttpHeaders.Host, "127.0.0.1:8787")
                }
                assertEquals(HttpStatusCode.Forbidden, unauthenticated.status)
                assertNull(received.get())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `admin route maps server failure to bad gateway`() = testApplication {
        val dir = releaseDir()
        try {
            withPublisherServer(status = HttpStatusCode.ServiceUnavailable, responseBody = "{}") { endpoint, _, _ ->
                application { module(dir, OtaPublisher(endpoint)) }
                val response = client.post("/api/update-pushes") {
                    header(HttpHeaders.Host, "127.0.0.1:8787")
                    header("X-Admin-Request", "1")
                }
                assertEquals(HttpStatusCode.BadGateway, response.status)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `admin route maps malformed result to bad gateway`() = testApplication {
        val dir = releaseDir()
        try {
            withPublisherServer(responseBody = "not json") { endpoint, _, _ ->
                application { module(dir, OtaPublisher(endpoint)) }
                val response = client.post("/api/update-pushes") {
                    header(HttpHeaders.Host, "127.0.0.1:8787")
                    header("X-Admin-Request", "1")
                }
                assertEquals(HttpStatusCode.BadGateway, response.status)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `admin route returns successful push statistics`() = testApplication {
        val dir = releaseDir()
        try {
            withPublisherServer { endpoint, received, _ ->
                application { module(dir, OtaPublisher(endpoint)) }
                val response = client.post("/api/update-pushes") {
                    header(HttpHeaders.Host, "127.0.0.1:8787")
                    header("X-Admin-Request", "1")
                }
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(
                    json.parseToJsonElement(json.encodeToString(result)),
                    Json.parseToJsonElement(response.bodyAsText())
                )
                assertNotNull(received.get())
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
