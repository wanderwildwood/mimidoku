package com.wanderwildwood.mimidoku.library

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
}
