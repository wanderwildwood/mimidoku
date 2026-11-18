package com.wanderwildwood.mimidoku.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerEntryTest {
    @Test fun `a pasted key loses the line break and spaces it brought with it`() {
        val entry = ServerEntry("192.168.0.10:13378", " eyJhbGciOi.abc-DEF_123\r\n")
        assertEquals("eyJhbGciOi.abc-DEF_123", entry.token)
        assertTrue(entry.isComplete)
    }

    @Test fun `a key broken over two lines is joined`() {
        assertEquals("abcdef", ServerEntry("nas", "abc\ndef").token)
        assertEquals("abcdef", ServerEntry("nas", "abc\tdef\u0000").token)
    }

    @Test fun `a key of nothing but white space is not a key`() {
        assertFalse(ServerEntry("nas", " \n\t").isComplete)
        assertFalse(ServerEntry(" ", "abc").isComplete)
    }
}
