package com.wanderwildwood.mimidoku.server

import com.wanderwildwood.mimidoku.data.BookEntity
import com.wanderwildwood.mimidoku.data.ChapterEntity
import com.wanderwildwood.mimidoku.data.LibraryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a server book's chapter files are fetched from: Audiobookshelf or a Nextcloud folder. */
interface BookFiles {
    /** The address of a chapter's audio, or null when the book is not this server's. */
    fun chapterUrl(bookUri: String, chapterUri: String): String?

    /** The one header every request carries. */
    fun authHeader(): Pair<String, String>
}

/** Every source whose books live on a server and are fetched whole before they play. */
fun isServerSource(sourceType: String): Boolean = sourceType == AbsSync.SOURCE_ABS || sourceType == NextcloudSync.SOURCE_NC

/**
 * Brings the books in a Nextcloud folder into the same tables as the books on the card and the
 * Audiobookshelf ones, the same way [AbsSync] does: ordinary rows carrying [SOURCE_NC], not on
 * the phone until they are downloaded whole, and never deleted by a sync.
 *
 * What a Nextcloud does not say, unlike Audiobookshelf, is how long anything is. A book's length
 * is learnt when it is downloaded, from the files, as a card book's is (see [AbsDownloader]).
 */
object NextcloudSync {

    const val SOURCE_NC = "NC"

    fun bookUri(path: String) = "$SOURCE_NC:$path"

    /** A chapter is its file; the marker keeps a one-file book's chapter apart from the book. */
    fun chapterUri(path: String) = "$SOURCE_NC:file:$path"

    fun filePathOf(chapterUri: String): String? =
        chapterUri.removePrefix("$SOURCE_NC:file:").takeIf { chapterUri.startsWith("$SOURCE_NC:file:") }

    suspend fun sync(client: NextcloudClient, dao: LibraryDao): AbsResult<AbsSync.SyncOutcome> {
        val found = when (val r = client.books()) {
            is AbsResult.Failure -> return r
            is AbsResult.Success -> r.value
        }
        val (books, chapters) = rows(found)
        withContext(Dispatchers.IO) {
            val fetched = books.map { it.uri }.toSet()
            // ⚠ As with Audiobookshelf: a sync never deletes anything the reader has. A book gone
            // from the folder stays on the shelf, with its downloaded audio, place and bookmarks,
            // until the reader removes it.
            val keep = AbsSync.keepUris(dao.bookUrisFrom(SOURCE_NC), fetched)
            dao.merge(books, chapters, at = System.currentTimeMillis(), sourceType = SOURCE_NC, stillThere = keep - fetched)
        }
        return AbsResult.Success(AbsSync.SyncOutcome(read = books.size, listed = books.size))
    }

    /** The rows for a listing: one book per [NcBook], one chapter per file, nothing kept yet. */
    fun rows(found: List<NcBook>): Pair<List<BookEntity>, List<ChapterEntity>> {
        val books = mutableListOf<BookEntity>()
        val chapters = mutableListOf<ChapterEntity>()
        for (book in found) {
            if (book.tracks.isEmpty()) continue
            val uri = bookUri(book.path)
            books += BookEntity(
                uri = uri,
                // The folder's name, as a card book's is: a card copy of the same folder is the
                // same book to the duplicate filter.
                name = book.path.substringAfterLast('/'),
                author = book.author,
                chapterCount = book.tracks.size,
                durationMs = 0L,
                genre = null,
                tagTitle = null,
                tagAuthor = null,
                currentChapterUri = null,
                positionMs = 0L,
                progressMs = 0L,
                lastPlayedAt = null,
                sourceType = SOURCE_NC,
                kept = false,
                seenAt = 0L,
            )
            book.tracks.forEachIndexed { index, track ->
                chapters += ChapterEntity(
                    uri = chapterUri(track.path),
                    bookUri = uri,
                    name = track.name.substringBeforeLast('.'),
                    sortIndex = index,
                    durationMs = 0L,
                    audioUri = "",
                )
            }
        }
        return books to chapters
    }
}
