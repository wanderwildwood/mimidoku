package com.wanderwildwood.mimidoku.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressTest {

    @Test
    fun playerLengthWinsOnceItHasRead() {
        assertEquals(600_000L, Progress.chapterLength(600_000, 590_000))
    }

    @Test
    fun storedLengthIsUsedBeforeThePlayerHasLoaded() {
        assertEquals(590_000L, Progress.chapterLength(0, 590_000))
    }

    @Test
    fun unreadableOrUnreadFilesAreUnknownNotNegative() {
        assertEquals(0L, Progress.chapterLength(0, -1))
        assertEquals(0L, Progress.chapterLength(0, 0))
    }

    @Test
    fun remainingIsRestOfChapterPlusLaterChapters() {
        assertEquals(60_000L + 300_000 + 200_000, Progress.remaining(listOf(300_000, 200_000), 180_000, 120_000))
    }

    @Test
    fun remainingSkipsUnreadLaterChapters() {
        assertEquals(60_000L + 200_000, Progress.remaining(listOf(-1, 200_000), 180_000, 120_000))
    }

    @Test
    fun remainingNeverGoesBelowZeroPastTheEnd() {
        assertEquals(0L, Progress.remaining(emptyList(), 100_000, 130_000))
    }

    @Test
    fun seekFractionSitsAtTheRightPlace() {
        assertEquals(0.25f, Progress.seekFraction(30_000, 120_000), 0.0001f)
        assertEquals(1f, Progress.seekFraction(150_000, 120_000), 0.0001f)
    }

    @Test
    fun seekFractionIsAtTheStartWhenLengthUnknown() {
        assertEquals(0f, Progress.seekFraction(119_000, 0), 0f)
    }
}
