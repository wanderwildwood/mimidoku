package com.wanderwildwood.mimidoku.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * A book as the app remembers it.
 *
 * Keyed by the document uri of its folder, because that is the one thing about a book that does
 * not change when the reader renames it or a provider reorders a listing.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val author: String?,
    val chapterCount: Int,
    /** Every chapter added up, or 0 while the durations are still being read. */
    val durationMs: Long,
    /** Whatever the file says, if it says anything. Not guessed at. */
    val genre: String?,
    /**
     * What the files call themselves, where they say. A folder is what the reader organised and a
     * tag is what the publisher wrote; the tag is usually the better name and the folder is the
     * one that is always there, so the tag wins and the folder catches it when it is missing.
     */
    val tagTitle: String?,
    val tagAuthor: String?,
    /** Which chapter was playing, by uri. Null until the book has been opened. */
    val currentChapterUri: String?,
    /** How far into that chapter, in milliseconds. */
    val positionMs: Long,
    /**
     * How far into the whole book, in milliseconds — the chapters already finished plus
     * [positionMs]. Stored rather than worked out on demand so that drawing a shelf does not run
     * an aggregate per row on the thread that draws it.
     */
    val progressMs: Long,
    /** Null until the book has been played, which is what "not started" means. */
    val lastPlayedAt: Long?,
    /**
     * Which scan last saw this book on the card. A scan stamps everything it found and then
     * deletes whatever is still carrying an older stamp, which is how books that have gone away
     * are noticed. Done this way rather than by listing what to keep, because that list is one
     * bound variable per book and SQLite stops accepting them somewhere under a thousand.
     */
    val seenAt: Long,
)

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val uri: String,
    val bookUri: String,
    val name: String,
    val sortIndex: Int,
    /** 0 until the file has been opened and asked. Reading it costs a seek, so it is done later. */
    val durationMs: Long,
)

/**
 * A place in a book the reader marked, or that the app marked for them.
 *
 * [automatic] is the difference between "I want to come back to this" and "this is where you were
 * when the timer ran out", which are worth telling apart in a list.
 */
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUri: String,
    val chapterUri: String,
    val positionMs: Long,
    val createdAt: Long,
    val automatic: Boolean,
)

/**
 * A chapter mark found inside a file.
 *
 * Separate from [ChapterEntity] because it is not a file: it is a place in one. A book delivered
 * as a single twelve-hour recording has one chapter and twenty of these.
 */
@Entity(tableName = "marks")
data class MarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUri: String,
    val chapterUri: String,
    val title: String,
    val startMs: Long,
)

@Dao
interface LibraryDao {

    @Query(
        // NOCASE, or a folder someone named in lower case sorts after every other book on the
        // shelf instead of among them.
        "SELECT * FROM books ORDER BY " +
            "IFNULL(tagAuthor, author) IS NULL, " +
            "IFNULL(tagAuthor, author) COLLATE NOCASE, " +
            "IFNULL(tagTitle, name) COLLATE NOCASE",
    )
    fun books(): Flow<List<BookEntity>>

    @Query("SELECT * FROM chapters WHERE bookUri = :bookUri ORDER BY sortIndex")
    suspend fun chaptersOf(bookUri: String): List<ChapterEntity>

    @Query("SELECT * FROM books WHERE uri = :uri")
    suspend fun book(uri: String): BookEntity?

    @Query("SELECT * FROM books WHERE uri = (SELECT bookUri FROM chapters WHERE uri = :chapterUri)")
    suspend fun bookOfChapter(chapterUri: String): BookEntity?

    /** The book to offer when the app is opened cold: whatever was being read last. */
    @Query("SELECT * FROM books WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 1")
    suspend fun lastRead(): BookEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    /**
     * Updates what a rescan can know, and deliberately not what it cannot: a book's position and
     * when it was last played belong to the reader, not to the disk, so a rescan must never
     * overwrite them.
     */
    @Query("UPDATE books SET name = :name, author = :author, chapterCount = :chapterCount WHERE uri = :uri")
    suspend fun refreshDetails(uri: String, name: String, author: String?, chapterCount: Int)

    @Query("SELECT * FROM chapters WHERE durationMs = 0 ORDER BY bookUri, sortIndex LIMIT :limit")
    suspend fun chaptersWithoutDuration(limit: Int): List<ChapterEntity>

    /**
     * Chapters are inserted with IGNORE so a rescan cannot throw away a duration that has already
     * been read, which means the things a rescan *can* know have to be written back explicitly.
     */
    @Query("UPDATE chapters SET name = :name, sortIndex = :sortIndex, bookUri = :bookUri WHERE uri = :uri")
    suspend fun refreshChapter(uri: String, bookUri: String, name: String, sortIndex: Int)

    @Query("UPDATE chapters SET durationMs = :durationMs WHERE uri = :uri")
    suspend fun setChapterDuration(uri: String, durationMs: Long)

    /**
     * A book's length is its chapters', and is stored rather than summed on every read: the
     * library screen would otherwise run one aggregate per row on the thread that draws it.
     */
    @Query("UPDATE books SET genre = :genre WHERE uri = :bookUri AND genre IS NULL")
    suspend fun setGenre(bookUri: String, genre: String?)

    @Query("UPDATE books SET tagTitle = :title WHERE uri = :bookUri AND tagTitle IS NULL")
    suspend fun setTagTitle(bookUri: String, title: String?)

    @Query("UPDATE books SET tagAuthor = :author WHERE uri = :bookUri AND tagAuthor IS NULL")
    suspend fun setTagAuthor(bookUri: String, author: String?)

    @Query("UPDATE books SET durationMs = (SELECT IFNULL(SUM(durationMs), 0) FROM chapters WHERE bookUri = :bookUri) WHERE uri = :bookUri")
    suspend fun refreshBookDuration(bookUri: String)

    @Query(
        """
        UPDATE books SET
            currentChapterUri = :chapterUri,
            positionMs = :positionMs,
            lastPlayedAt = :at,
            progressMs = :positionMs + IFNULL((
                SELECT SUM(durationMs) FROM chapters
                WHERE bookUri = :uri AND durationMs > 0 AND sortIndex < (
                    SELECT sortIndex FROM chapters WHERE uri = :chapterUri
                )
            ), 0)
        WHERE uri = :uri
        """,
    )
    suspend fun rememberPosition(uri: String, chapterUri: String, positionMs: Long, at: Long)

    @Query("UPDATE books SET seenAt = :at WHERE uri = :uri")
    suspend fun markSeen(uri: String, at: Long)

    @Query("DELETE FROM books WHERE seenAt < :at")
    suspend fun deleteBooksUnseenSince(at: Long)

    @Query("DELETE FROM chapters WHERE bookUri NOT IN (SELECT uri FROM books)")
    suspend fun deleteOrphanedChapters()

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Query("SELECT * FROM marks WHERE bookUri = :bookUri ORDER BY startMs")
    suspend fun marksOf(bookUri: String): List<MarkEntity>

    @Query("SELECT chapterCount FROM books WHERE uri = :bookUri")
    suspend fun chapterCountOf(bookUri: String): Int?

    @Query("SELECT COUNT(*) FROM marks WHERE bookUri = :bookUri")
    suspend fun markCount(bookUri: String): Int

    /**
     * The one file of every book that is one file and has no marks against it.
     *
     * Marks are read when a file's length is, which happens once and never again — so a book
     * already in the library when the app learnt to read a new kind of chapter list would keep
     * its empty list for ever. This is how those books are found and asked a second time.
     */
    @Query(
        "SELECT * FROM chapters WHERE bookUri IN " +
            "(SELECT uri FROM books WHERE chapterCount = 1) " +
            "AND bookUri NOT IN (SELECT bookUri FROM marks)",
    )
    suspend fun singleChaptersWithoutMarks(): List<ChapterEntity>

    @Insert
    suspend fun addMarks(marks: List<MarkEntity>)

    @Query("DELETE FROM marks WHERE bookUri NOT IN (SELECT uri FROM books)")
    suspend fun deleteOrphanedMarks()

    @Query("SELECT * FROM bookmarks WHERE bookUri = :bookUri ORDER BY createdAt DESC")
    fun bookmarks(bookUri: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun addBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("DELETE FROM bookmarks WHERE bookUri NOT IN (SELECT uri FROM books)")
    suspend fun deleteOrphanedBookmarks()

    /**
     * Folds a scan into what is already known.
     *
     * New books are inserted, known ones have their details refreshed, and books that are no
     * longer on the card are dropped. A book that is still there keeps where the reader was in it,
     * which is the whole point of remembering.
     */
    @Transaction
    suspend fun merge(books: List<BookEntity>, chapters: List<ChapterEntity>, at: Long) {
        insertBooks(books)
        books.forEach {
            refreshDetails(it.uri, it.name, it.author, it.chapterCount)
            markSeen(it.uri, at)
        }
        insertChapters(chapters)
        chapters.forEach { refreshChapter(it.uri, it.bookUri, it.name, it.sortIndex) }
        // Everything this scan did not stamp is no longer on the card, and everything that
        // belonged to it goes with it.
        deleteBooksUnseenSince(at)
        deleteOrphanedChapters()
        deleteOrphanedBookmarks()
        deleteOrphanedMarks()
    }
}

@Database(
    entities = [BookEntity::class, ChapterEntity::class, BookmarkEntity::class, MarkEntity::class],
    version = 8,
    exportSchema = false,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        @Volatile private var instance: LibraryDatabase? = null

        fun get(context: Context): LibraryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LibraryDatabase::class.java,
                    "library.db",
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
            }
    }
}
