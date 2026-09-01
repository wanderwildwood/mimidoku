package com.wanderwildwood.mimidoku.data

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import com.wanderwildwood.mimidoku.library.ChapterMarks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Fills in how long each chapter is, afterwards.
 *
 * A file's length is not in the directory listing: it costs opening the file and reading its
 * header, tens of milliseconds each, and a library of a few hundred books has thousands of files.
 * Doing that during a scan would mean the library takes a minute to appear instead of half a
 * second, so it happens here, in the background, and the screen fills in as it goes.
 *
 * Written in batches so a reader who opens the app and leaves keeps whatever was learned.
 */
class DurationReader(private val context: Context) {

    private val dao = LibraryDatabase.get(context).dao()

    suspend fun run() = withContext(Dispatchers.IO) {
        while (true) {
            coroutineContext.ensureActive()
            val batch = dao.chaptersWithoutDuration(BATCH)
            if (batch.isEmpty()) {
                reread()
                return@withContext
            }
            val touched = mutableSetOf<String>()
            for (chapter in batch) {
                coroutineContext.ensureActive()
                val read = read(chapter.uri)
                // A book that is one file names itself in its title tag and usually has no album
                // at all; a book that is a folder of files puts the book in the album and the
                // chapter in the title. Taking the title as the book's name in that second case
                // would name every book after its first chapter.
                val single = dao.chapterCountOf(chapter.bookUri) == 1
                // A file that cannot be read gets -1 rather than 0, so it is not asked about again
                // on every pass. Something the reader has to fix is not something to retry forever.
                dao.setChapterDuration(chapter.uri, read?.durationMs ?: -1L)
                // Whatever the first file that has one says; these queries only write where
                // nothing has been written yet, so later files cannot overrule the first.
                read?.genre?.let { dao.setGenre(chapter.bookUri, it) }
                (read?.album ?: read?.title?.takeIf { single })
                    ?.let { dao.setTagTitle(chapter.bookUri, it) }
                read?.author?.let { dao.setTagAuthor(chapter.bookUri, it) }
                marks(chapter, single)
                touched += chapter.bookUri
            }
            touched.forEach { dao.refreshBookDuration(it) }
        }
    }

    /**
     * Asks the books that are already known for their chapters again, once.
     *
     * A file is only ever opened for its marks when its length is read, which happens the first
     * time it is seen — so when this app learns to read a kind of chapter list it could not read
     * before, every book already in the library would go on showing none. [Preferences.marksPass]
     * says which reading a library has had; a library behind the current one is read again, and
     * only the books that have nothing are opened.
     */
    private suspend fun reread() {
        val preferences = Preferences.of(context)
        if (preferences.marksPass >= MARKS_PASS) return
        for (chapter in dao.singleChaptersWithoutMarks()) {
            coroutineContext.ensureActive()
            marks(chapter, single = true)
        }
        preferences.marksPass = MARKS_PASS
    }

    /**
     * A book that is one long file has its chapters inside it. Only asked of books that are a
     * single file: where a book is already a folder of files, the files are the chapters and
     * opening every one of them again to look would be work for nothing.
     */
    private suspend fun marks(chapter: ChapterEntity, single: Boolean) {
        if (!single) return
        if (dao.markCount(chapter.bookUri) > 0) return
        val found = ChapterMarks.read(context.contentResolver, chapter.uri.toUri())
        // One mark at the start is the same as no marks, and a list of one is worse than none:
        // it looks like a book whose chapters failed to load.
        if (found.size < 2) return
        dao.addMarks(
            found.map {
                MarkEntity(
                    bookUri = chapter.bookUri,
                    chapterUri = chapter.uri,
                    title = it.title,
                    startMs = it.startMs,
                )
            },
        )
    }

    private class Read(
        val durationMs: Long,
        val genre: String?,
        val album: String?,
        val title: String?,
        val author: String?,
    )

    /**
     * One open of the file answers both questions. Opening it twice would double the cost of the
     * only expensive thing this class does.
     */
    private fun read(uri: String): Read? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri.toUri())
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return@use null
            fun tag(key: Int) = retriever.extractMetadata(key)?.trim()?.ifBlank { null }
            Read(
                durationMs = ms,
                genre = tag(MediaMetadataRetriever.METADATA_KEY_GENRE),
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE),
                // The plain artist first. In an audiobook it is usually the credit the reader
                // would recognise — often author and narrator together — while the album artist
                // is frequently whatever the ripping tool put there.
                author = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
            )
        }
    }.getOrNull()

    private companion object {
        /** Small enough that stopping mid-library loses a second of work, not a minute of it. */
        const val BATCH = 40

        /** Raised whenever the reading of chapter lists changes, which asks every book again. */
        const val MARKS_PASS = 1
    }
}
