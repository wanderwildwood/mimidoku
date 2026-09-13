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

/**
 * What the reader says a granted folder holds.
 *
 * Two folders can look identical and mean opposite things: twenty numbered files are twenty
 * chapters of one recording in one library and twenty whole books in another, and nothing in the
 * folder says which. Where it cannot be known, the reader is asked instead of guessed at.
 */
enum class Reading(val label: String) {
    /** Books, however they are filed - a folder each, a file each, or authors holding both. */
    Books("Books"),

    /** This folder is one book and everything under it is a chapter of it. */
    OneBook("One book"),
}

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
 * Deliberately not clever. It looks at where the audio actually sits and takes that at face value:
 * a folder holding audio is a book, a folder holding folders is a shelf, and a file loose on a
 * shelf is a book of its own. Where that book sits does not matter - an author who files a trilogy
 * under a series folder, or a reader who hands over the folder above the one that was meant, is
 * followed down rather than lost.
 *
 * The one thing it does not work out for itself is the chosen folder: a folder of audio files is
 * one book to one reader and a shelf of one-file books to the next, and the folder cannot say
 * which. That is [Reading], and it is answered rather than guessed.
 */
class BookScanner(private val resolver: ContentResolver) {

    /**
     * [reading] is what the reader said this folder holds, and null is what the app assumed before
     * anyone was asked: a folder of audio with nothing else in it read as a single book. That
     * assumption is kept for folders granted before there was a question, and dropped as soon as
     * one is answered.
     */
    suspend fun scan(treeUri: Uri, reading: Reading? = null): ScanResult = coroutineScope {
        val root = DocumentsContract.getTreeDocumentId(treeUri)
        val entries = children(treeUri, root)

        val audioHere = entries.filter { it.isAudio }
        val foldersHere = entries.filter { it.isDirectory }

        if (reading == Reading.OneBook) {
            // Said to be one book, so everything under it is a chapter of it - subfolders
            // included, because a long book is often handed over as CD1 and CD2 rather than as
            // one list. The discs come in the order their folders sort, the same order their
            // files would have had.
            val discs = foldersHere.sortedWith(compareBy(NATURAL) { it.name })
                .flatMap { children(treeUri, it.documentId).filter { e -> e.isAudio }.toChapters() }
            val chapters = audioHere.toChapters() + discs
            return@coroutineScope if (chapters.isEmpty()) {
                ScanResult(TreeShape.Empty, emptyList())
            } else {
                ScanResult(TreeShape.SingleBook, listOf(Book(nameOf(treeUri, root), null, chapters)))
            }
        }

        // Audio directly in the chosen folder and nothing else: before the reader could say, this
        // was taken for one book. It is as likely to be a shelf of books that each arrived as one
        // file, which is why the question exists; the old reading is kept only where there is no
        // answer to go by.
        if (reading == null && audioHere.isNotEmpty() && foldersHere.isEmpty()) {
            return@coroutineScope ScanResult(
                shape = TreeShape.SingleBook,
                books = listOf(Book(nameOf(treeUri, root), null, audioHere.toChapters())),
            )
        }

        if (foldersHere.isEmpty() && audioHere.isEmpty()) {
            return@coroutineScope ScanResult(TreeShape.Empty, emptyList())
        }

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
    private suspend fun readBooks(
        treeUri: Uri,
        folders: List<Entry>,
        author: String?,
        depth: Int = 0,
    ): List<Book> = coroutineScope {
        folders.map { folder ->
            async {
                val inside = children(treeUri, folder.documentId)
                val chapters = inside.filter { it.isAudio }.toChapters()
                when {
                    // Audio here: this folder is the book. Whatever else it holds - artwork, a
                    // bonus disc, a folder of ripping logs - is not part of the reading.
                    chapters.isNotEmpty() -> listOf(Book(folder.name, author, chapters))
                    // No audio here, but folders that might hold some: a series filed between the
                    // author and the books, or a shelf sitting a level deeper than this expected.
                    // Following it costs one listing per folder. Not following it threw the books
                    // underneath away without saying so - eleven folders and thirty-odd books in
                    // the library this was found in, among them whole series.
                    depth < DEEPEST ->
                        readBooks(treeUri, inside.filter { it.isDirectory }, author, depth + 1)
                    else -> emptyList()
                }
            }
        }.awaitAll().flatten()
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
         * How far below a shelf to go on looking for books.
         *
         * A book is normally one level down, and a series folder puts it two. This is not a
         * description of anyone's library: it is the point at which a tree that turns out to be
         * something other than books - a whole card handed over by mistake - stops being walked.
         */
        const val DEEPEST = 6

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
