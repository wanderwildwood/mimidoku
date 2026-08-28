package com.wanderwildwood.mimidoku.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** One audio file. A book is made of these in the order they sort. */
data class Chapter(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
)

/**
 * A book, as found on disk.
 *
 * [author] is only set when the folder above the book named one — nothing is inferred from tags
 * here. What the reader organised is what gets shown.
 */
data class Book(
    val name: String,
    val author: String?,
    val chapters: List<Chapter>,
)

/** What the chosen folder turned out to be. */
enum class TreeShape {
    /** Audio sitting directly in the chosen folder: the folder is one book. */
    SingleBook,

    /** Folders that hold audio: each one is a book. */
    BooksInFolders,

    /** Folders whose own folders hold the audio: the first level names authors. */
    AuthorsThenBooks,

    /** Nothing readable was found. Says so rather than guessing. */
    Empty,
}

data class ScanResult(
    val shape: TreeShape,
    val books: List<Book>,
)

/**
 * Reads a document tree the reader has granted, and works out what is in it.
 *
 * Deliberately not clever. It looks at where the audio actually sits and takes that at face value,
 * rather than asking the reader to describe their own folders. Three layouts cover every library
 * worth supporting, and a library that is two of them at once is not a library anyone has.
 */
class BookScanner(private val resolver: ContentResolver) {

    suspend fun scan(treeUri: Uri): ScanResult = coroutineScope {
        val root = DocumentsContract.getTreeDocumentId(treeUri)
        val entries = children(treeUri, root)

        val audioHere = entries.filter { it.isAudio }
        val foldersHere = entries.filter { it.isDirectory }

        // Audio directly in the chosen folder: this is one book, named after the folder.
        if (audioHere.isNotEmpty() && foldersHere.isEmpty()) {
            return@coroutineScope ScanResult(
                shape = TreeShape.SingleBook,
                books = listOf(Book(nameOf(treeUri, root), null, audioHere.toChapters())),
            )
        }

        if (foldersHere.isEmpty()) return@coroutineScope ScanResult(TreeShape.Empty, emptyList())

        // Each folder is asked what it is rather than the tree being asked once, and it may be
        // both. Real libraries are mixed - an author with a folder per book and a handful of
        // single-file recordings dropped in beside them - and a tree judged as a whole silently
        // loses whichever half it decided against.
        val found = foldersHere.map { folder ->
            async {
                val inside = children(treeUri, folder.documentId)
                val shelved = readBooks(treeUri, inside.filter { it.isDirectory }, author = folder.name)
                // A file sitting loose under an author is a whole recording of its own, not a
                // chapter of its neighbours: nothing groups it with them except the folder, and
                // the folder is the author. A book whose parts are separate files keeps them in a
                // folder of its own, which is the case above.
                val loose = inside.filter { it.isAudio }.map { it.asBook(folder.name) }
                Read(author = shelved.isNotEmpty() || loose.isNotEmpty(), books = shelved + loose)
            }
        }.awaitAll()

        // Loose files at the very top have nobody to file them under.
        val unshelved = audioHere.map { it.asBook(author = null) }

        val books = found.flatMap { it.books } + unshelved
        ScanResult(
            shape = when {
                books.isEmpty() -> TreeShape.Empty
                found.any { it.author && it.books.isNotEmpty() } -> TreeShape.AuthorsThenBooks
                else -> TreeShape.BooksInFolders
            },
            books = books,
        )
    }

    private class Read(val author: Boolean, val books: List<Book>)

    /**
     * Every folder is a separate query to the storage provider, and each costs tens of
     * milliseconds. Read sequentially, a library of any size spends most of a minute waiting on
     * round trips that do not depend on each other. These run together instead, with a ceiling so
     * the provider is asked a reasonable amount at once rather than everything at once.
     */
    private suspend fun readBooks(treeUri: Uri, folders: List<Entry>, author: String?): List<Book> =
        coroutineScope {
            folders.map { folder ->
                async {
                    val chapters = children(treeUri, folder.documentId).filter { it.isAudio }.toChapters()
                    if (chapters.isEmpty()) null else Book(folder.name, author, chapters)
                }
            }.awaitAll().filterNotNull()
        }

    /** One file that is a whole book on its own, named after itself until a tag says otherwise. */
    private fun Entry.asBook(author: String?) =
        Book(name.substringBeforeLast('.'), author, listOf(Chapter(uri, name, size)))

    private fun List<Entry>.toChapters(): List<Chapter> =
        sortedWith(compareBy(NATURAL) { it.name })
            .map { Chapter(it.uri, it.name, it.size) }

    private suspend fun children(treeUri: Uri, parentDocumentId: String): List<Entry> =
        gate.withPermit { withContext(Dispatchers.IO) { queryChildren(treeUri, parentDocumentId) } }

    private fun queryChildren(treeUri: Uri, parentDocumentId: String): List<Entry> {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        return runCatching {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0) ?: continue
                        val name = cursor.getString(1) ?: continue
                        // A name beginning with a dot is not something the reader put there to read.
                        if (name.startsWith(".")) continue
                        add(
                            Entry(
                                documentId = id,
                                name = name,
                                mimeType = cursor.getString(2).orEmpty(),
                                size = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun nameOf(treeUri: Uri, documentId: String): String =
        documentId.substringAfterLast('/').ifBlank { treeUri.lastPathSegment.orEmpty() }

    private data class Entry(
        val documentId: String,
        val name: String,
        val mimeType: String,
        val size: Long,
        val uri: Uri,
    ) {
        val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
        val isAudio: Boolean get() = !isDirectory && name.substringAfterLast('.', "").lowercase() in AUDIO
    }

    private val gate = Semaphore(CONCURRENT_QUERIES)

    private companion object {
        /**
         * Enough to hide the latency, and no more: measured on a 155-book library on an exFAT
         * microSD, 12 gave 13.5s and 32 gave 13.8s. The provider serialises internally past about
         * a dozen, so asking for more only queues.
         */
        const val CONCURRENT_QUERIES = 12

        /**
         * Matched on extension rather than reported mime type: providers disagree about m4b, and
         * an audiobook that will not appear because a provider called it application/octet-stream
         * is a bad trade for strictness.
         */
        val AUDIO = setOf(
            "mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav", "mka", "mp4", "3gp",
        )

        /** "Chapter 10" belongs after "Chapter 9", which a plain string sort gets wrong. */
        val NATURAL = Comparator<String> { a, b ->
            val ap = a.splitDigits()
            val bp = b.splitDigits()
            var i = 0
            while (i < ap.size && i < bp.size) {
                val x = ap[i]
                val y = bp[i]
                val cmp = if (x is Long && y is Long) x.compareTo(y)
                else x.toString().compareTo(y.toString(), ignoreCase = true)
                if (cmp != 0) return@Comparator cmp
                i++
            }
            ap.size - bp.size
        }

        fun String.splitDigits(): List<Any> =
            Regex("\\d+|\\D+").findAll(this).map { part ->
                part.value.toLongOrNull() ?: part.value
            }.toList()
    }
}
