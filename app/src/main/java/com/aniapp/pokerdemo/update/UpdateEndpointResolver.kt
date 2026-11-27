package com.aniapp.pokerdemo.update

import com.aniapp.pokerdemo.net.ServerAddress
import java.net.InetAddress

data class UpdateEndpoint(val host: String, val secure: Boolean) {
    val baseUrl: String
        get() = "${if (secure) "https" else "http"}://${authority()}:8787"

    val metadataUrl: String get() = "$baseUrl/api/apk"
    val downloadUrl: String get() = "$baseUrl/download/latest.apk"

    private fun authority(): String {
        val normalized = normalizeHost(host) ?: host
        return if (':' in normalized) "[${normalized.replace("%", "%25")}]" else normalized
    }

    companion object {
        const val UPDATE_PORT = 8787
        private val IPV4_LITERAL = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")

        fun resolve(
            address: ServerAddress,
            dns: (String) -> List<InetAddress> = { name -> InetAddress.getAllByName(name).toList() }
        ): UpdateEndpoint? {
            val host = normalizeHost(address.host) ?: return null
            if (host.isEmpty() || host.any { it.isWhitespace() || it in "/?#@\\" }) return null
            if (address.port !in 1..65535) return null

            val ipLiteral = isIpLiteral(host)
            val literalAddress = if (ipLiteral) runCatching { InetAddress.getByName(host) }.getOrNull() else null
            val allowed = if (ipLiteral) {
                literalAddress != null && isPrivateOrLinkLocal(literalAddress)
            } else {
                val addresses = runCatching { dns(host) }.getOrNull().orEmpty()
                addresses.isNotEmpty() && addresses.all(::isPrivateOrLinkLocal)
            }
            if (!allowed) return null
            return UpdateEndpoint(host, address.secure)
        }

        private fun normalizeHost(raw: String): String? {
            val value = raw.trim()
            if (value.startsWith('[') && value.endsWith(']')) return value.substring(1, value.length - 1)
            return value
        }

        private fun isIpLiteral(host: String): Boolean =
            host.contains(':') || IPV4_LITERAL.matches(host)

        private fun isPrivateOrLinkLocal(address: InetAddress): Boolean {
            if (address.isLoopbackAddress || address.isAnyLocalAddress || address.isMulticastAddress) return false
            if (address is java.net.Inet6Address) {
                val bytes = address.address
                if (bytes != null && bytes.size == 16 && (bytes[0].toInt() and 0xff in 0xfc..0xfd)) return true
            }
            return address.isSiteLocalAddress || address.isLinkLocalAddress
        }
    }
}
