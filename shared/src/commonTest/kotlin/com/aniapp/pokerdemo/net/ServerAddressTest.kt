package com.aniapp.pokerdemo.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerAddressTest {
    @Test
    fun wssDefaultsTo443() {
        assertEquals(ServerAddress("xxx.trycloudflare.com", 443, true), ServerAddress.parse("wss://xxx.trycloudflare.com"))
    }

    @Test
    fun wssWithExplicitPort() {
        assertEquals(ServerAddress("poker.example.com", 8443, true), ServerAddress.parse("wss://poker.example.com:8443"))
    }

    @Test
    fun wsSchemeDefaultsTo8080() {
        assertEquals(ServerAddress("192.168.1.5", 8080, false), ServerAddress.parse("ws://192.168.1.5"))
    }

    @Test
    fun bareHostWithPort() {
        assertEquals(ServerAddress("192.168.1.5", 9000, false), ServerAddress.parse("192.168.1.5:9000"))
    }

    @Test
    fun bareHostDefaultsTo8080() {
        assertEquals(ServerAddress("192.168.1.5", 8080, false), ServerAddress.parse("192.168.1.5"))
    }

    @Test
    fun trimsWhitespaceAndCaseInsensitiveScheme() {
        assertEquals(ServerAddress("a.b", 443, true), ServerAddress.parse("  WSS://a.b  "))
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(ServerAddress.parse(""))
        assertNull(ServerAddress.parse("   "))
        assertNull(ServerAddress.parse("https://a.b"))
        assertNull(ServerAddress.parse("http://a.b:8080"))
        assertNull(ServerAddress.parse("wss://"))
        assertNull(ServerAddress.parse("a.b:abc"))
        assertNull(ServerAddress.parse("a.b:0"))
        assertNull(ServerAddress.parse("a.b:65536"))
        assertNull(ServerAddress.parse("a.b:"))
        assertNull(ServerAddress.parse(":8080"))
        assertNull(ServerAddress.parse("wss://a.b/path"))
        assertNull(ServerAddress.parse("a b"))
    }
}