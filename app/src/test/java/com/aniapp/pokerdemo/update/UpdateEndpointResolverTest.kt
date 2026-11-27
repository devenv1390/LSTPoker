package com.aniapp.pokerdemo.update

import com.aniapp.pokerdemo.net.ServerAddress
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateEndpointResolverTest {
    @Test
    fun `derives update port from private ipv4`() {
        val endpoint = UpdateEndpoint.resolve(ServerAddress("192.168.3.18", 8080, false))!!
        assertEquals("192.168.3.18", endpoint.host)
        assertFalse(endpoint.secure)
        assertEquals("http://192.168.3.18:8787/api/apk", endpoint.metadataUrl)
    }

    @Test
    fun `maps secure websocket to https`() {
        val endpoint = UpdateEndpoint.resolve(ServerAddress("10.0.0.8", 443, true))!!
        assertEquals("https://10.0.0.8:8787/download/latest.apk", endpoint.downloadUrl)
    }

    @Test
    fun `brackets private ipv6 in url`() {
        val endpoint = UpdateEndpoint.resolve(ServerAddress("fd00::8", 8080, false))!!
        assertEquals("http://[fd00::8]:8787/api/apk", endpoint.metadataUrl)
    }

    @Test
    fun `accepts bracket input and link local ipv4`() {
        val endpoint = UpdateEndpoint.resolve(ServerAddress("[fd00::8]", 8080, false))!!
        assertEquals("fd00::8", endpoint.host)
        assertEquals("169.254.1.2", UpdateEndpoint.resolve(ServerAddress("169.254.1.2", 8080, false))?.host)
    }

    @Test
    fun `accepts hostname only when every address is private`() {
        val addresses = { _: String -> listOf(addr("192.168.1.2"), addr("fd00::2")) }
        assertTrue(UpdateEndpoint.resolve(ServerAddress("poker.local", 8080, false), addresses) != null)
        assertTrue(UpdateEndpoint.resolve(ServerAddress("my.poker.mac.local", 8080, false), addresses) != null)
        assertNull(
            UpdateEndpoint.resolve(
                ServerAddress("poker.local", 8080, false),
                { _ -> listOf(addr("192.168.1.2"), addr("8.8.8.8")) }
            )
        )
    }

    @Test
    fun `escapes ipv6 zone in url`() {
        val endpoint = UpdateEndpoint("fe80::1%wlan0", false)
        assertEquals("http://[fe80::1%25wlan0]:8787/api/apk", endpoint.metadataUrl)
    }

    @Test
    fun `rejects public loopback multicast and invalid input`() {
        listOf("8.8.8.8", "127.0.0.1", "224.0.0.1", "example.com").forEach { host ->
            assertNull(UpdateEndpoint.resolve(ServerAddress(host, 8080, false)))
        }
        assertNull(UpdateEndpoint.resolve(ServerAddress("192.168.1.2", 0, false)))
        assertNull(UpdateEndpoint.resolve(ServerAddress("192.168.1.2/path", 8080, false)))
    }

    private fun addr(value: String): InetAddress = InetAddress.getByName(value)
}
