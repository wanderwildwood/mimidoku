package com.wanderwildwood.mimidoku.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RenamedTest {

    @Test
    fun `a new book holding a gone book's files carries it on`() {
        val pairs = Renamed.pairs(
            newChapters = mapOf("b/00 intro.mp3" to listOf("b/00 intro.mp3", "b/02.mp3", "b/03.mp3")),
            ownerBefore = mapOf("b/02.mp3" to "b/01.mp3", "b/03.mp3" to "b/01.mp3"),
            going = setOf("b/01.mp3"),
        )
        assertEquals(mapOf("b/00 intro.mp3" to "b/01.mp3"), pairs)
    }

    @Test
    fun `a book still on the card, or one sharing nothing, is not carried`() {
        assertEquals(
            emptyMap<String, String>(),
            Renamed.pairs(
                newChapters = mapOf("n/1.mp3" to listOf("n/1.mp3")),
                ownerBefore = mapOf("x/2.mp3" to "x/1.mp3"),
                going = setOf("x/1.mp3"),
            ),
        )
        assertEquals(
            emptyMap<String, String>(),
            Renamed.pairs(
                newChapters = mapOf("b/0.mp3" to listOf("b/0.mp3", "b/2.mp3")),
                ownerBefore = mapOf("b/2.mp3" to "b/1.mp3"),
                going = emptySet(),
            ),
        )
    }

    @Test
    fun `a gone book goes to one new book only`() {
        val pairs = Renamed.pairs(
            newChapters = linkedMapOf("a" to listOf("a", "x1"), "b" to listOf("b", "x2", "x3")),
            ownerBefore = mapOf("x1" to "old", "x2" to "old", "x3" to "old"),
            going = setOf("old"),
        )
        assertEquals(1, pairs.size)
    }

    @Test
    fun `a renamed first file maps to the one new file, the rest to themselves`() {
        val map = Renamed.chapterMap(
            oldChapters = listOf("01 one.mp3", "02.mp3", "03.mp3"),
            newChapters = listOf("01 One.mp3", "02.mp3", "03.mp3"),
        )
        assertEquals(mapOf("01 one.mp3" to "01 One.mp3", "02.mp3" to "02.mp3", "03.mp3" to "03.mp3"), map)
    }

    @Test
    fun `a file added in front keeps the place on the files that stayed`() {
        val map = Renamed.chapterMap(
            oldChapters = listOf("01.mp3", "02.mp3"),
            newChapters = listOf("00.mp3", "01.mp3", "02.mp3"),
        )
        assertEquals(mapOf("01.mp3" to "01.mp3", "02.mp3" to "02.mp3"), map)
    }

    @Test
    fun `two lost and two new files are not guessed at`() {
        val map = Renamed.chapterMap(listOf("a", "b", "c"), listOf("A", "B", "c"))
        assertEquals(mapOf("c" to "c"), map)
    }
}
