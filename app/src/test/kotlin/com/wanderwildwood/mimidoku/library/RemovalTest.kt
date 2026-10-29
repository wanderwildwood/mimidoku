package com.wanderwildwood.mimidoku.library

import com.wanderwildwood.mimidoku.data.BookEntity
import com.wanderwildwood.mimidoku.data.SOURCE_LOCAL
import com.wanderwildwood.mimidoku.data.SOURCE_OPENED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovalTest {

    private fun book(uri: String, source: String, kept: Boolean = true) = BookEntity(
        uri = uri, name = uri, author = null, chapterCount = 1, durationMs = 0L, genre = null,
        tagTitle = null, tagAuthor = null, currentChapterUri = null, positionMs = 0L, progressMs = 0L,
        lastPlayedAt = null, sourceType = source, kept = kept, seenAt = 0L,
    )

    @Test
    fun `only a fetched copy of a server book is ever deleted`() {
        val books = listOf(
            book("card", SOURCE_LOCAL),
            book("other-app", SOURCE_OPENED),
            book("server-fetched", "ABS", kept = true),
            book("server-listed", "ABS", kept = false),
        )
        assertEquals(listOf("server-fetched"), Removal.serverCopies(books).map { it.uri })
    }

    @Test
    fun `a server book is never written into the hidden set`() {
        val books = listOf(book("card", SOURCE_LOCAL), book("server-fetched", "ABS"), book("other-app", SOURCE_OPENED))
        assertEquals(listOf("card"), Removal.hiding(books))
    }

    @Test
    fun `nothing to hide or delete for a list with only listed server books`() {
        val books = listOf(book("a", "ABS", kept = false), book("b", "ABS", kept = false))
        assertTrue(Removal.serverCopies(books).isEmpty())
        assertTrue(Removal.hiding(books).isEmpty())
    }
}
