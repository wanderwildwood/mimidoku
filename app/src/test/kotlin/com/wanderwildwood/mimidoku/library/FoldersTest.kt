package com.wanderwildwood.mimidoku.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldersTest {

    @Test
    fun aGrantedFolderIsReadEveryTimeTheAppOpens() {
        assertTrue(Folders.scanOnOpen(folders = 1, cardBooks = 0))
        assertTrue(Folders.scanOnOpen(folders = 2, cardBooks = 14))
    }

    @Test
    fun booksLeftBehindByARemovedFolderAreClearedOnTheNextOpen() {
        assertTrue(Folders.scanOnOpen(folders = 0, cardBooks = 2))
    }

    @Test
    fun withNoFolderAndNoCardBooksThereIsNothingToRead() {
        assertFalse(Folders.scanOnOpen(folders = 0, cardBooks = 0))
    }

    @Test
    fun booksOfAFolderThatCannotBeReadAreKept() {
        val tree = "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3AAudiobooks"
        val other = "content://com.android.externalstorage.documents/tree/primary%3ABooks"
        val books = listOf(
            "$tree/document/1A2B-3C4D%3AAudiobooks%2FAda%20Whitlock%2F01.mp3",
            "$other/document/primary%3ABooks%2FTomas%20Reyes%2F01.mp3",
        )
        assertEquals(listOf(books[0]), Folders.keptFrom(listOf(tree), books))
    }

    @Test
    fun aFolderWhoseNameStartsTheSameIsNotTheUnreadableOne() {
        val tree = "content://com.android.externalstorage.documents/tree/primary%3ABooks"
        val book = "content://com.android.externalstorage.documents/tree/primary%3ABooks2/document/primary%3ABooks2%2F01.mp3"
        assertEquals(emptyList<String>(), Folders.keptFrom(listOf(tree), listOf(book)))
    }

    @Test
    fun withEveryFolderReadableNothingIsHeldBack() {
        assertEquals(emptyList<String>(), Folders.keptFrom(emptyList(), listOf("content://x/tree/a/document/b")))
    }
}
