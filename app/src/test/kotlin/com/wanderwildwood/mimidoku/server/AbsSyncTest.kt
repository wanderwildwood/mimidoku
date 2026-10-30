package com.wanderwildwood.mimidoku.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AbsSyncTest {
    private val local = listOf("ABS:a", "ABS:b", "ABS:c")

    @Test fun `an empty listing removes nothing`() {
        assertEquals(local.toSet(), AbsSync.keepUris(local, emptySet()))
    }

    @Test fun `a book the server flags missing or no longer lists is kept`() {
        val kept = AbsSync.keepUris(local, setOf("ABS:a"))
        assertTrue(kept.containsAll(local))
    }

    @Test fun `books read now are added`() {
        assertEquals(setOf("ABS:a", "ABS:b", "ABS:c", "ABS:d"), AbsSync.keepUris(local, setOf("ABS:d")))
    }

    @Test fun `sync stops after three failures in a row`() {
        assertEquals(3, AbsSync.GIVE_UP_AFTER)
    }
}
