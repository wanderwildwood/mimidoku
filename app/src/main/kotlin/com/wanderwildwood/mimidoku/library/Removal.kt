package com.wanderwildwood.mimidoku.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.wanderwildwood.mimidoku.data.BookEntity
import com.wanderwildwood.mimidoku.data.LibraryDao
import com.wanderwildwood.mimidoku.data.SOURCE_LOCAL
import com.wanderwildwood.mimidoku.data.SOURCE_OPENED
import com.wanderwildwood.mimidoku.server.AbsDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Taking books off the phone.
 *
 * What goes depends on where the book came from, and nothing here ever reaches a server:
 *
 * - **On the card**: the book's own audio files, then any folder they leave holding no audio and
 *   no folders -- the book's folder with its cover, and the author's once their last book has
 *   gone. Never the granted folder itself, and never anything outside one.
 * - **From a server**: the copy that was fetched, by the same path as giving one back, so the
 *   book stays listed as one to fetch and the server's copy is untouched.
 * - **Opened from another app**: only the row. The file is that app's, not this one's.
 */
object Removal {

    sealed interface Outcome {
        data object Done : Outcome

        /** The folder was granted for reading only, so nothing was touched. */
        data object ReadOnly : Outcome

        /** Some files would not go; [left] of them are still on the phone. */
        data class Partly(val left: Int) : Outcome
    }

    /**
     * Whether any of these books sits in a folder granted for reading only -- asked of every file
     * before any of them goes, because a book half removed when the second folder it touched
     * turned out to be read-only is worse than one left whole. Folders granted before this app
     * could remove anything were granted that way, and choosing the folder again fixes it.
     */
    suspend fun readOnly(context: Context, dao: LibraryDao, books: List<BookEntity>): Boolean =
        withContext(Dispatchers.IO) {
            val writable = context.contentResolver.persistedUriPermissions
                .filter { it.isWritePermission }
                .mapNotNull { runCatching { DocumentsContract.getTreeDocumentId(it.uri) }.getOrNull() }
                .toSet()
            books.filter { it.sourceType == SOURCE_LOCAL }
                .flatMap { dao.chaptersOf(it.uri) }
                .any { treeOf(it.uri.toUri()).let { tree -> tree == null || tree !in writable } }
        }

    suspend fun remove(context: Context, dao: LibraryDao, books: List<BookEntity>): Outcome =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            if (readOnly(context, dao, books)) return@withContext Outcome.ReadOnly
            val onCard = books.filter { it.sourceType == SOURCE_LOCAL }
            val chapters = onCard.associateWith { dao.chaptersOf(it.uri) }

            var left = 0
            val emptied = mutableSetOf<Pair<Uri, String>>()
            for ((book, parts) in chapters) {
                for (part in parts) {
                    val uri = part.uri.toUri()
                    val gone = runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)
                    if (!gone) {
                        left++
                        continue
                    }
                    val root = treeOf(uri) ?: continue
                    val documentId = DocumentsContract.getDocumentId(uri)
                    emptied += treeUri(uri, root) to documentId
                }
                dao.forgetBook(book.uri)
            }
            for ((tree, documentId) in emptied) tidyUp(context, tree, documentId)

            for (book in books) {
                when (book.sourceType) {
                    SOURCE_LOCAL -> Unit
                    SOURCE_OPENED -> dao.forgetBook(book.uri)
                    else -> if (book.kept) AbsDownloader.remove(context, dao, book.uri)
                }
            }
            if (left > 0) Outcome.Partly(left) else Outcome.Done
        }

    /**
     * Climbs from a removed file's folder towards the granted one, taking each folder that now
     * holds no audio and no folders, and stopping at the first that still does -- or at the
     * granted folder, which is never taken.
     */
    private fun tidyUp(context: Context, tree: Uri, removedDocumentId: String) {
        val root = DocumentsContract.getTreeDocumentId(tree)
        var folder = removedDocumentId.substringBeforeLast('/', "")
        while (folder.startsWith("$root/")) {
            if (holdsAnything(context, tree, folder)) return
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, folder)
            if (!runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)) return
            folder = folder.substringBeforeLast('/', "")
        }
    }

    /** Whether a folder still has audio or another folder in it, which is to say a book or more. */
    private fun holdsAnything(context: Context, tree: Uri, folder: String): Boolean {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        // A folder that cannot be listed is assumed full: not knowing is not a reason to delete.
        val cursor = runCatching { context.contentResolver.query(children, columns, null, null, null) }
            .getOrNull() ?: return true
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(0).orEmpty()
                val mime = it.getString(1).orEmpty()
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) return true
                if (mime.startsWith("audio/")) return true
                if (name.substringAfterLast('.', "").lowercase() in BookScanner.AUDIO) return true
            }
        }
        return false
    }

    /** The granted folder a document was reached through, or null if it was not reached through one. */
    private fun treeOf(uri: Uri): String? =
        runCatching { if (DocumentsContract.isTreeUri(uri)) DocumentsContract.getTreeDocumentId(uri) else null }
            .getOrNull()

    private fun treeUri(document: Uri, root: String): Uri =
        DocumentsContract.buildTreeDocumentUri(document.authority, root)

    private fun String.toUri(): Uri = Uri.parse(this)
}
