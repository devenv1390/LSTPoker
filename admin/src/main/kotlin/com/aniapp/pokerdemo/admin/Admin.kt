package com.aniapp.pokerdemo.admin

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

private const val ADMIN_PORT = 8787
private val apkContentType = ContentType("application", "vnd.android.package-archive")
private val dashboardHtml: String =
    Sys::class.java.getResourceAsStream("/dashboard.html")!!.bufferedReader().readText()
private val updateHtml: String =
    Sys::class.java.getResourceAsStream("/update.html")!!.bufferedReader().readText()

fun main() {
    Sys.startPublicProbeLoop()
    embeddedServer(CIO, port = ADMIN_PORT, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module(
    releaseDir: File = File(Sys.root, "app/build/outputs/apk/release"),
    otaPublisher: OtaPublisher = OtaPublisher()
) {
    val apkDistribution = ApkDistribution(releaseDir)
    install(ContentNegotiation) {
        json(Json { encodeDefaults = true })
    }
    intercept(ApplicationCallPipeline.Plugins) {
        val hostHeader = call.request.headers[HttpHeaders.Host] ?: ""
        val hostname = hostnameOf(hostHeader)
        val loopbackHost = hostname in setOf("127.0.0.1", "localhost", "::1")
        val remoteHost = call.request.local.remoteHost.lowercase()
        val loopbackRemote = remoteHost in setOf("127.0.0.1", "localhost", "::1", "0:0:0:0:0:0:0:1")
        val proxied = call.request.headers["Cf-Ray"] != null ||
            call.request.headers["Cf-Connecting-Ip"] != null ||
            call.request.headers["X-Forwarded-For"] != null ||
            call.request.headers["Forwarded"] != null
        val readOnlyLanPath = call.request.httpMethod in setOf(HttpMethod.Get, HttpMethod.Head) &&
            call.request.path() in setOf("/update", "/api/apk", "/download/latest.apk")

        // 管理 API 必须同时来自回环连接和回环 Host，防止局域网请求伪造 Host 绕过限制。
        if (proxied || ((!loopbackHost || !loopbackRemote) && !(readOnlyLanPath && isPrivateOrLocalHostname(hostname)))) {
            call.respondText("管理接口仅限本机；手机更新请访问 /update", status = HttpStatusCode.Forbidden)
            finish()
            return@intercept
        }
        if (call.request.httpMethod == HttpMethod.Options) {
            call.respondText("forbidden", status = HttpStatusCode.Forbidden)
            finish()
            return@intercept
        }
        if (call.request.httpMethod == HttpMethod.Post && call.request.headers["X-Admin-Request"] != "1") {
            call.respondText("missing X-Admin-Request", status = HttpStatusCode.Forbidden)
            finish()
            return@intercept
        }
    }
    routing {
        get("/update") {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respondText(updateHtml, ContentType.Text.Html)
        }
        get("/api/apk") {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val release = withContext(Dispatchers.IO) { apkDistribution.latest() }
            if (release == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "尚未生成 Release APK"))
            } else {
                call.respond(release.info)
            }
        }
        get("/download/latest.apk") {
            val release = withContext(Dispatchers.IO) { apkDistribution.latest() }
            if (release == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "尚未生成 Release APK"))
                return@get
            }
            val etag = "\"${release.info.sha256}\""
            if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) {
                call.response.header(HttpHeaders.ETag, etag)
                call.respond(HttpStatusCode.NotModified)
                return@get
            }
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.response.header(HttpHeaders.ETag, etag)
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${release.file.name}\"")
            call.response.header(HttpHeaders.ContentLength, release.file.length().toString())
            call.response.header("X-Content-Type-Options", "nosniff")
            call.respond(LocalFileContent(release.file, apkContentType))
        }
        head("/download/latest.apk") {
            val release = withContext(Dispatchers.IO) { apkDistribution.latest() }
            if (release == null) {
                call.respond(HttpStatusCode.NotFound)
                return@head
            }
            val etag = "\"${release.info.sha256}\""
            if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) {
                call.response.header(HttpHeaders.ETag, etag)
                call.respond(HttpStatusCode.NotModified)
                return@head
            }
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.response.header(HttpHeaders.ETag, etag)
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${release.file.name}\"")
            call.response.header(HttpHeaders.ContentLength, release.file.length().toString())
            call.response.header(HttpHeaders.ContentType, apkContentType.toString())
            call.response.header("X-Content-Type-Options", "nosniff")
            call.respond(HttpStatusCode.OK)
        }
        get("/") {
            call.respondText(dashboardHtml, ContentType.Text.Html)
        }
        get("/api/status") {
            call.respond(withContext(Dispatchers.IO) { Sys.buildStatus() })
        }
        post("/api/update-pushes") {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val release = withContext(Dispatchers.IO) { apkDistribution.latest() }
            if (release == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "尚未生成 Release APK"))
                return@post
            }
            try {
                call.respond(otaPublisher.publish(release))
            } catch (_: OtaPublishException) {
                call.respond(HttpStatusCode.BadGateway, mapOf("error" to "OTA 服务器不可达或响应无效"))
            }
        }
        post("/api/service/{action}") {
            val action = call.parameters["action"]
            if (action !in setOf("start", "stop", "restart", "rebuild")) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val ok = Sys.startOp(action!!)
            if (ok) call.respond(HttpStatusCode.Accepted, mapOf("op" to action))
            else call.respond(HttpStatusCode.Conflict, mapOf("error" to "已有操作进行中"))
        }
        post("/api/tunnel/restart") {
            val ok = Sys.startOp("tunnel")
            if (ok) call.respond(HttpStatusCode.Accepted, mapOf("op" to "tunnel"))
            else call.respond(HttpStatusCode.Conflict, mapOf("error" to "已有操作进行中"))
        }
        get("/api/logs/{name}") {
            val f = LogTail.files[call.parameters["name"]]
            if (f == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val n = call.request.queryParameters["tail"]?.toIntOrNull()?.coerceIn(1, 2000) ?: 200
            call.respond(mapOf("lines" to withContext(Dispatchers.IO) { LogTail.tail(f, n) }))
        }
        get("/api/logs/{name}/stream") {
            val f = LogTail.files[call.parameters["name"]]
            if (f == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.response.header("X-Accel-Buffering", "no")
            call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                LogTail.follow(f, 200) { line ->
                    if (line == ":hb") writeStringUtf8(": hb\n\n")
                    else writeStringUtf8("data: ${line.replace("\n", " ")}\n\n")
                    flush()
                }
            }
        }
    }
}

private fun hostnameOf(hostHeader: String): String {
    val value = hostHeader.trim().lowercase()
    return if (value.startsWith("[")) value.substringAfter('[').substringBefore(']').substringBefore('%')
    else value.substringBefore(':')
}

private fun isPrivateOrLocalHostname(hostname: String): Boolean =
    isPrivateIpv4(hostname) || isPrivateIpv6(hostname) || hostname.endsWith(".local")

private fun isPrivateIpv4(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != 4) return false
    val nums = parts.map { it.toIntOrNull() ?: return false }
    if (nums.any { it !in 0..255 }) return false
    val (a, b) = nums
    return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
}

private fun isPrivateIpv6(host: String): Boolean {
    val value = host.substringBefore('%').lowercase()
    if (value.startsWith("fc") || value.startsWith("fd")) return true
    if (!value.startsWith("fe")) return false
    val prefix = value.take(4).toIntOrNull(16) ?: return false
    return prefix in 0xfe80..0xfebf
}
