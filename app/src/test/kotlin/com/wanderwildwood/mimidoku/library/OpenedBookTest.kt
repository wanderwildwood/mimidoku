package com.wanderwildwood.mimidoku.library

import com.wanderwildwood.mimidoku.glance.NowReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenedBookTest {

    @Test
    fun aBookIsNamedAfterItsFile() {
        assertEquals("The Willow Road", OpenedBook.bookName("The Willow Road.m4b"))
        assertEquals("The Willow Road", OpenedBook.bookName("/storage/emulated/0/Download/The Willow Road.m4b"))
        assertEquals("vol.2.the.ford", OpenedBook.bookName("vol.2.the.ford.m4b"))
        assertEquals(".m4b", OpenedBook.bookName(".m4b"))
    }

    private val marks = listOf("book" to 0L, "book" to 60_000L, "book" to 120_000L)

    @Test
    fun theChapterIsTheLastMarkPassed() {
        assertEquals(1, NowReading.partNumber(marks, "book", 0L))
        assertEquals(1, NowReading.partNumber(marks, "book", 59_999L))
        assertEquals(2, NowReading.partNumber(marks, "book", 60_000L))
        assertEquals(3, NowReading.partNumber(marks, "book", 500_000L))
    }

    @Test
    fun aBookOfFilesCountsItsFiles() {
        val files = listOf("one" to 0L, "two" to 0L, "three" to 0L)
        assertEquals(2, NowReading.partNumber(files, "two", 12_345L))
    }

    @Test
    fun beforeTheFirstMarkIsTheFirstChapterOfThatFile() {
        val late = listOf("a" to 0L, "b" to 5_000L, "b" to 9_000L)
        assertEquals(2, NowReading.partNumber(late, "b", 1_000L))
        assertNull(NowReading.partNumber(late, "elsewhere", 1_000L))
    }

    @Test
    fun timeLeftIsWrittenLikeThePlayer() {
        assertEquals("2:13:05", NowReading.clock(((2 * 60 + 13) * 60 + 5) * 1000L))
        assertEquals("5:09", NowReading.clock(309_000L))
        assertEquals("0:00", NowReading.clock(-4_000L))
    }

    @Test
    fun thePositionRunsOnWhilePlaying() {
        val now = NowReading.Now("book", 10_000L, 0L, isPlaying = true, speed = 1.5f, at = 1_000L)
        assertEquals(25_000L, now.positionAt(11_000L))
        assertEquals(10_000L, now.copy(isPlaying = false).positionAt(11_000L))
    }
}
