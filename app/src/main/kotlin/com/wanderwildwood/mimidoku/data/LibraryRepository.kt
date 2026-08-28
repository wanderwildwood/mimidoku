package com.wanderwildwood.mimidoku.data

import android.content.Context
import android.net.Uri
import com.wanderwildwood.mimidoku.library.Book
import com.wanderwildwood.mimidoku.library.BookScanner
import com.wanderwildwood.mimidoku.library.TreeShape
import kotlinx.coroutines.flow.Flow

/**
 * What the app knows about the reader's books.
 *
 * The point of this layer is that opening the app never waits on the card. What is known is
 * returned immediately from the database; a scan is something that happens afterwards and quietly
 * corrects it.
 */
class LibraryRepository(private val context: Context) {

    private val dao = LibraryDatabase.get(context).dao()

    val books: Flow<List<BookEntity>> = dao.books()

    suspend fun known(): Int = dao.count()

    /**
     * Reads the card and folds the result into what is already known.
     *
     * Positions survive: a book that is still there keeps where the reader was, because a rescan
     * knows about files and nothing about reading.
     */
    suspend fun rescan(trees: List<Uri>): Map<String, TreeShape> {
        val scanner = BookScanner(context.contentResolver)
        val results = trees.associate { it.toString() to scanner.scan(it) }

        // Merged across every granted folder before writing: merge() removes what it did not see,
        // so handing it one folder at a time would have each scan delete the others' books.
        val found = results.values.flatMap { it.books }.distinctBy { it.chapters.first().uri }
        val books = found.map { it.toEntity() }
        val chapters = found.flatMap { book ->
            book.chapters.mapIndexed { index, chapter ->
                ChapterEntity(
                    uri = chapter.uri.toString(),
                    bookUri = book.identity(),
                    name = chapter.name,
                    sortIndex = index,
                    durationMs = 0L,
                )
            }
        }
        dao.merge(books, chapters, at = System.currentTimeMillis())
        return results.mapValues { it.value.shape }
    }

    suspend fun rememberPosition(bookUri: String, chapterUri: String, positionMs: Long) {
        dao.rememberPosition(bookUri, chapterUri, positionMs, System.currentTimeMillis())
    }

    suspend fun chaptersOf(bookUri: String): List<ChapterEntity> = dao.chaptersOf(bookUri)

    fun bookmarks(bookUri: String): Flow<List<BookmarkEntity>> = dao.bookmarks(bookUri)

    suspend fun addBookmark(bookUri: String, chapterUri: String, positionMs: Long, automatic: Boolean) {
        dao.addBookmark(
            BookmarkEntity(
                bookUri = bookUri,
                chapterUri = chapterUri,
                positionMs = positionMs,
                createdAt = System.currentTimeMillis(),
                automatic = automatic,
            ),
        )
    }

    suspend fun deleteBookmark(id: Long) = dao.deleteBookmark(id)

    suspend fun book(uri: String): BookEntity? = dao.book(uri)

    suspend fun bookOfChapter(chapterUri: String): BookEntity? = dao.bookOfChapter(chapterUri)

    suspend fun lastRead(): BookEntity? = dao.lastRead()

    suspend fun marksOf(bookUri: String): List<MarkEntity> = dao.marksOf(bookUri)

    /**
     * A book is identified by its first chapter's uri.
     *
     * The folder's own uri would be the obvious choice, but the scanner reaches books through
     * their contents and a folder that holds one loose file has no folder of its own. The first
     * chapter is always there.
     */
    private fun Book.identity(): String = chapters.first().uri.toString()

    private fun Book.toEntity() = BookEntity(
        uri = identity(),
        name = name,
        author = author,
        chapterCount = chapters.size,
        durationMs = 0L,
        genre = null,
        tagTitle = null,
        tagAuthor = null,
        currentChapterUri = null,
        positionMs = 0L,
        progressMs = 0L,
        lastPlayedAt = null,
        seenAt = 0L,
    )
}
