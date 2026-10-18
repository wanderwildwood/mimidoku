package com.wanderwildwood.mimidoku.shelf

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.wanderwildwood.mimidoku.data.LibraryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Whether this library has a book, for Shelf (com.wanderwildwood.shoka), which shows "Listen" on
 * a book's page only when it does. It answers Shelf alone, and only about the one book asked
 * for: `content://com.wanderwildwood.mimidoku.books/find?title=…&author=…` gives one row per
 * matching book, `book` (the key [MainActivity] opens with [ACTION_OPEN_BOOK]) and `title`, or
 * none. The library itself is never listed.
 */
class ShelfLookup : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (callingPackage != ASKER) return null
        val context = context ?: return null
        val title = uri.getQueryParameter("title")?.takeIf { it.isNotBlank() } ?: return null
        val author = uri.getQueryParameter("author")?.takeIf { it.isNotBlank() }
        val cursor = MatrixCursor(arrayOf("book", "title"))
        try {
            // Asked on a binder thread of Shelf's call, which can wait for one read.
            val books = runBlocking { LibraryDatabase.get(context).dao().books().first() }
            books.filter { b ->
                ShelfMatch.matches(title, author, b.tagTitle ?: b.name, b.author ?: b.tagAuthor) ||
                    (b.tagTitle != null && ShelfMatch.matches(title, author, b.name, b.author ?: b.tagAuthor))
            }.forEach { cursor.addRow(arrayOf<Any?>(it.uri, it.tagTitle ?: it.name)) }
        } catch (_: Exception) {
            // Nothing found is the answer when the library cannot be read.
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        const val ASKER = "com.wanderwildwood.shoka"

        /** Opens one book, named by the `book` extra, as a tap on it in the library would. */
        const val ACTION_OPEN_BOOK = "com.wanderwildwood.mimidoku.OPEN_BOOK"
        const val EXTRA_BOOK = "book"
    }
}
