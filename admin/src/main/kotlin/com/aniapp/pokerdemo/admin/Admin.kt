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
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val dashboardHtml: String =
    Sys::class.java.getResourceAsStream("/dashboard.html")!!.bufferedReader().readText()

fun main() {
    Sys.startPublicProbeLoop()
    embeddedServer(CIO, port = 8787, host = "127.0.0.1") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json { encodeDefaults = true })
    }
    intercept(ApplicationCallPipeline.Plugins) {
        val hostHeader = call.request.headers[HttpHeaders.Host] ?: ""
        val hostname = if (hostHeader.startsWith("[")) {
            hostHeader.substringAfter('[').substringBefore(']')
        } else {
            hostHeader.substringBefore(':')
        }
        if (hostname !in setOf("127.0.0.1", "localhost", "::1")) {
            call.respondText("forbidden host", status = HttpStatusCode.Forbidden)
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
        get("/") {
            call.respondText(dashboardHtml, ContentType.Text.Html)
        }
        get("/api/status") {
            call.respond(withContext(Dispatchers.IO) { Sys.buildStatus() })
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