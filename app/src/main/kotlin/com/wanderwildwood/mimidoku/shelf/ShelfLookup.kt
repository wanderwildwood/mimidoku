package com.wanderwildwood.mimidoku.shelf

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.wanderwildwood.mimidoku.data.LibraryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest

/**
 * Whether this library has a book, for Shelf (com.wanderwildwood.shoka), which shows "Listen" on
 * a book's page only when it does. It answers Shelf alone, by its name and its release
 * certificate, and only about the one book asked for:
 * `content://com.wanderwildwood.mimidoku.books/find?title=…&author=…` gives one row per
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
        val context = context ?: return null
        if (!fromShelf(context)) return null
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

    /**
     * Whether the caller is Shelf: its package name, and signed with Shelf's release certificate
     * and nothing else. A name alone is not enough - an app can take Shelf's name on a phone where
     * Shelf is not installed. A debuggable build of this app answers any Shelf, for testing.
     */
    private fun fromShelf(context: Context): Boolean {
        if (callingPackage != ASKER) return false
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) return true
        return runCatching {
            val signers = context.packageManager
                .getPackageInfo(ASKER, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            !signers.isNullOrEmpty() && signers.all { cert ->
                MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
                    .joinToString("") { "%02x".format(it) } == ASKER_CERT_SHA256
            }
        }.getOrDefault(false)
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        const val ASKER = "com.wanderwildwood.shoka"

        /** SHA-256 of the certificate Shelf's releases are signed with, lower-case hex. */
        const val ASKER_CERT_SHA256 = "7479c85dab42dd4e97629ccff0b7eb62e1f09722b9da86ab781737286a5c6342"

        /** Opens one book, named by the `book` extra, as a tap on it in the library would. */
        const val ACTION_OPEN_BOOK = "com.wanderwildwood.mimidoku.OPEN_BOOK"
        const val EXTRA_BOOK = "book"
    }
}
