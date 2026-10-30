package com.wanderwildwood.mimidoku.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressTest {
    @Test fun `a bare home address gets http`() {
        assertEquals("http://192.168.0.10:13378", ServerAddress.withScheme("192.168.0.10:13378"))
        assertEquals("http://nas:13378", ServerAddress.withScheme(" nas:13378 "))
        assertEquals("http://books.local", ServerAddress.withScheme("books.local"))
        assertEquals("http://100.101.1.2:80", ServerAddress.withScheme("100.101.1.2:80"))
    }

    @Test fun `a bare address anywhere else gets https`() {
        assertEquals("https://books.example.com", ServerAddress.withScheme("books.example.com"))
        assertEquals("https://8.8.8.8", ServerAddress.withScheme("8.8.8.8"))
        assertEquals("https://172.32.0.1", ServerAddress.withScheme("172.32.0.1"))
    }

    @Test fun `a typed scheme is kept`() {
        assertEquals("http://books.example.com", ServerAddress.withScheme("http://books.example.com"))
        assertEquals("https://192.168.1.2", ServerAddress.withScheme("https://192.168.1.2"))
    }

    @Test fun `plain http outside the house is flagged, inside it is not`() {
        assertTrue(ServerAddress.plainHttpOutsideHome("http://books.example.com"))
        assertTrue(ServerAddress.plainHttpOutsideHome("http://8.8.8.8:13378"))
        assertFalse(ServerAddress.plainHttpOutsideHome("http://192.168.1.5:13378"))
        assertFalse(ServerAddress.plainHttpOutsideHome("books.example.com"))
        assertFalse(ServerAddress.plainHttpOutsideHome("https://books.example.com"))
        assertFalse(ServerAddress.plainHttpOutsideHome("http://[fd00::1]:80"))
    }
}
