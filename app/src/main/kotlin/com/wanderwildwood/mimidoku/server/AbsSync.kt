package com.wanderwildwood.mimidoku.server

import com.wanderwildwood.mimidoku.data.BookEntity
import com.wanderwildwood.mimidoku.data.ChapterEntity
import com.wanderwildwood.mimidoku.data.LibraryDao
import com.wanderwildwood.mimidoku.data.MarkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Brings a server's books into the same tables as the books on the card.
 *
 * They go in as ordinary rows carrying [SOURCE_ABS], which is what puts them on the shelf beside
 * everything else without any screen knowing a server exists. The one difference a reader has to
 * be told is that a server book is not on the phone yet, and that is the download's business
 * rather than the library's.
 *
 * What the server saves us is most of a scan. A card has to be walked, and then every file on it
 * opened to learn how long it is; a server already knows its books' lengths, their authors and
 * where the chapters fall, so a synced book arrives complete and [DurationReader] never has to
 * touch it -- which is just as well, because it could not: opening an http url through a
 * ContentResolver is not a thing that works.
 */
object AbsSync {

    /** Consecutive books the server fails to give before the sync stops. */
    const val GIVE_UP_AFTER = 3

    /** What a sync did: books read, out of books the server listed as fetchable. */
    data class SyncOutcome(val read: Int, val listed: Int)

    /** A book that lives on a server. Namespaced, because a server id is short and plain. */
    const val SOURCE_ABS = "ABS"

    fun bookUri(itemId: String) = "$SOURCE_ABS:$itemId"

    fun chapterUri(itemId: String, ino: String) = "$SOURCE_ABS:$itemId:$ino"

    /** The item a row belongs to, for asking the server about it again. */
    fun itemIdOf(bookUri: String): String? =
        bookUri.removePrefix("$SOURCE_ABS:").takeIf { bookUri.startsWith("$SOURCE_ABS:") }

    /**
     * Fetches a whole library and folds it in.
     *
     * One request per book is the API's shape -- the listing carries no files and no chapters --
     * so this reports progress and a library of a hundred books takes a moment.
     */
    suspend fun sync(
        client: AbsClient,
        libraryId: String,
        dao: LibraryDao,
        /** Not used: a sync never removes audio. Kept so callers need not change. */
        context: android.content.Context? = null,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): AbsResult<SyncOutcome> {
        val listing = when (val r = client.listing(libraryId)) {
            is AbsResult.Failure -> return r
            is AbsResult.Success -> r.value
        }
        val ids = listing.present

        val books = mutableListOf<BookEntity>()
        val chapters = mutableListOf<ChapterEntity>()
        val marks = mutableListOf<MarkEntity>()

        var failuresInARow = 0
        var withoutFiles = 0
        for ((index, id) in ids.withIndex()) {
            when (val r = client.book(id)) {
                // One book the server stumbles over does not throw away the ninety-nine that
                // worked. It simply is not in this sync, and the next one can pick it up.
                is AbsResult.Failure -> {
                    // But a server that is gone answers every request with a ten second wait.
                    // After a few in a row it is stopped, and nothing is changed.
                    if (++failuresInARow >= GIVE_UP_AFTER) return r
                }
                is AbsResult.Success -> {
                    failuresInARow = 0
                    val book = r.value
                    if (book.tracks.isNotEmpty()) collect(book, books, chapters, marks) else withoutFiles++
                }
            }
            onProgress(index + 1, ids.size)
        }

        // ⚠ A sync never deletes anything the reader has. A book the server flags missing (a NAS
        // that is off, a library being rescanned), a book whose details timed out, and a book that
        // is no longer listed at all all stay exactly as they are, with their downloaded audio,
        // place and bookmarks: the server being wrong or away must not cost the only local copy.
        // A book that really is gone is removed by the reader, through the Remove dialog, which
        // asks first.
        val fetched = books.map { it.uri }.toSet()

        withContext(Dispatchers.IO) {
            val keep = keepUris(dao.bookUrisFrom(SOURCE_ABS), fetched)
            dao.merge(
                books, chapters, at = System.currentTimeMillis(), sourceType = SOURCE_ABS,
                stillThere = keep - fetched
            )
            // Written after the merge, which is what clears the old ones: marks hang off books,
            // and a book that survived the merge keeps the marks it had.
            for (book in books) {
                if (dao.markCount(book.uri) == 0) {
                    marks.filter { it.bookUri == book.uri }
                        .takeIf { it.size >= 2 }
                        ?.let { dao.addMarks(it) }
                }
            }
            // The server said how long everything is, so the lengths are final rather than
            // something to be measured later.
            books.forEach { dao.refreshBookDuration(it.uri) }
        }

        return AbsResult.Success(SyncOutcome(read = books.size, listed = ids.size - withoutFiles))
    }

    /**
     * Every server book the sync leaves on the shelf: all the ones already there, plus the ones it
     * has just read. Nothing is dropped, whatever the listing said, even an empty one.
     */
    fun keepUris(local: Collection<String>, fetched: Set<String>): Set<String> = local.toSet() + fetched

    private fun collect(
        book: AbsBook,
        books: MutableList<BookEntity>,
        chapters: MutableList<ChapterEntity>,
        marks: MutableList<MarkEntity>,
    ) {
        val uri = bookUri(book.id)
        books += BookEntity(
            uri = uri,
            // The folder, as a card book's name is its folder: a card copied from this server
            // holds the same folder under the same name, and the two are matched by it. What is
            // shown is the title, which goes in the tag below.
            name = book.folder ?: book.title,
            author = book.author,
            chapterCount = book.tracks.size,
            durationMs = book.tracks.sumOf { it.durationMs },
            genre = book.genre,
            // The server's title and author are already the good ones -- they are what a
            // librarian typed, not what a ripping tool left behind -- so they go straight in
            // rather than waiting for a file to be opened and asked.
            tagTitle = book.title,
            tagAuthor = book.author,
            currentChapterUri = null,
            positionMs = 0L,
            progressMs = 0L,
            lastPlayedAt = null,
            sourceType = SOURCE_ABS,
            // Nothing is fetched by syncing. A catalogue is a list of what could be had.
            kept = false,
            seenAt = 0L,
        )
        book.tracks.forEachIndexed { index, track ->
            chapters += ChapterEntity(
                uri = chapterUri(book.id, track.ino),
                bookUri = uri,
                name = track.name,
                sortIndex = index,
                durationMs = track.durationMs,
                // Where it is *today*. A download rewrites this to a file and the row is
                // otherwise untouched, which is how a bookmark survives being kept.
                audioUri = "",
            )
        }
        // Only for a book that is one long recording. Where a book is already a folder of files
        // the files are its chapters, and a second list laid over them would only disagree --
        // this library has a seventeen-file book the server gives fifteen chapters for.
        if (book.tracks.size == 1) {
            val only = chapterUri(book.id, book.tracks.first().ino)
            marks += book.marks.map {
                MarkEntity(bookUri = uri, chapterUri = only, title = it.title, startMs = it.startMs)
            }
        }
    }
}
