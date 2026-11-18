package com.aniapp.pokerdemo.net

data class ServerAddress(val host: String, val port: Int, val secure: Boolean) {
    companion object {
        fun parse(input: String): ServerAddress? {
            val s = input.trim()
            if (s.isEmpty()) return null
            val secure: Boolean
            val rest: String
            when {
                s.startsWith("wss://", ignoreCase = true) -> {
                    secure = true
                    rest = s.substring(6)
                }
                s.startsWith("ws://", ignoreCase = true) -> {
                    secure = false
                    rest = s.substring(5)
                }
                s.contains("://") -> return null
                else -> {
                    secure = false
                    rest = s
                }
            }
            if (rest.isEmpty() || rest.any { it.isWhitespace() || it in "/?#@\\" }) return null
            val colon = rest.lastIndexOf(':')
            val host: String
            val port: Int
            if (colon >= 0) {
                host = rest.substring(0, colon)
                port = rest.substring(colon + 1).toIntOrNull() ?: return null
            } else {
                host = rest
                port = if (secure) 443 else 8080
            }
            if (host.isEmpty() || port !in 1..65535) return null
            return ServerAddress(host, port, secure)
        }
    }
}