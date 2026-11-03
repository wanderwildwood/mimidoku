package com.wanderwildwood.mimidoku.library

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant

/**
 * The reader's places and bookmarks, as one plain JSON file written and read through the system's
 * file picker. It goes where the reader puts it and nowhere else.
 *
 * Only what reading made is in it: where each book was left, when, and the bookmarks. Not the
 * books themselves, which are the reader's files, and not the server key, which stays sealed on
 * this phone. Field names are data, not interface, and stay in English.
 *
 * A book is found again by its uri, and failing that by its title and author, because a new phone
 * or a folder granted again can give the same book a different address. A chapter is found by its
 * uri, then by its name, then by where it sits in the book.
 *
 * [read] checks the whole file and throws a [BackupException] for anything it cannot use; only
 * then does the caller touch the library, in one transaction.
 */
object PlacesBackup {

    const val FORMAT = "mimidoku-places"
    const val VERSION = 1

    data class SavedBookmark(
        val chapterUri: String,
        val chapterIndex: Int,
        val chapterName: String,
        val positionMs: Long,
        val createdAt: Long,
        val automatic: Boolean,
    )

    data class SavedBook(
        val uri: String,
        val title: String,
        val author: String?,
        /** Null for a book that has bookmarks but was never played. */
        val chapterUri: String?,
        val chapterIndex: Int,
        val chapterName: String,
        val positionMs: Long,
        val lastPlayedAt: Long?,
        val bookmarks: List<SavedBookmark>,
    )

    /** A book as the library has it now, reduced to what matching needs. */
    data class LibraryBook(val uri: String, val title: String, val author: String?, val chapters: List<LibraryChapter>)
    data class LibraryChapter(val uri: String, val index: Int, val name: String)

    data class Place(val bookUri: String, val chapterUri: String, val positionMs: Long, val lastPlayedAt: Long)
    data class Mark(val bookUri: String, val chapterUri: String, val positionMs: Long, val createdAt: Long, val automatic: Boolean)

    /** What a restore will write: the places, the bookmarks per book (which replace that book's), and what had no book here. */
    data class Plan(val places: List<Place>, val bookmarks: Map<String, List<Mark>>, val notFound: Int) {
        val books: Int get() = (places.map { it.bookUri } + bookmarks.keys).toSet().size
    }

    class BackupException(val kind: Kind) : Exception(kind.name) {
        enum class Kind { NOT_A_BACKUP, NEWER, DAMAGED }
    }

    fun write(books: List<SavedBook>, written: Instant = Instant.now()): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("written", written.toString())
        root.put("books", JSONArray().apply {
            for (b in books.sortedBy { it.uri }) put(JSONObject().apply {
                put("uri", b.uri)
                put("title", b.title)
                put("author", b.author ?: JSONObject.NULL)
                put("chapter_uri", b.chapterUri ?: JSONObject.NULL)
                put("chapter_index", b.chapterIndex)
                put("chapter_name", b.chapterName)
                put("position_ms", b.positionMs)
                put("last_played", b.lastPlayedAt ?: JSONObject.NULL)
                put("bookmarks", JSONArray().apply {
                    for (m in b.bookmarks.sortedWith(compareBy({ it.createdAt }, { it.positionMs }))) put(JSONObject().apply {
                        put("chapter_uri", m.chapterUri)
                        put("chapter_index", m.chapterIndex)
                        put("chapter_name", m.chapterName)
                        put("position_ms", m.positionMs)
                        put("created", m.createdAt)
                        put("automatic", m.automatic)
                    })
                })
            })
        })
        return root.toString(2)
    }

    /** Reads a backup whole, or throws a [BackupException]; nothing is half-read. */
    fun read(text: String): List<SavedBook> {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupException(BackupException.Kind.NOT_A_BACKUP)
        }
        if (root.optString("format") != FORMAT) throw BackupException(BackupException.Kind.NOT_A_BACKUP)
        val version = root.optInt("version", -1)
        if (version > VERSION) throw BackupException(BackupException.Kind.NEWER)
        if (version < 1) throw BackupException(BackupException.Kind.DAMAGED)
        try {
            val ba = root.getJSONArray("books")
            val books = (0 until ba.length()).map { i ->
                val o = ba.getJSONObject(i)
                val ma = o.getJSONArray("bookmarks")
                SavedBook(
                    uri = o.getString("uri").also { require(it.isNotBlank()) },
                    title = o.getString("title"),
                    author = if (o.isNull("author")) null else o.getString("author"),
                    chapterUri = if (o.isNull("chapter_uri")) null else o.getString("chapter_uri"),
                    chapterIndex = o.getInt("chapter_index").also { require(it >= 0) },
                    chapterName = o.getString("chapter_name"),
                    positionMs = o.getLong("position_ms").also { require(it >= 0) },
                    lastPlayedAt = if (o.isNull("last_played")) null else o.getLong("last_played"),
                    bookmarks = (0 until ma.length()).map { j ->
                        val m = ma.getJSONObject(j)
                        SavedBookmark(
                            chapterUri = m.getString("chapter_uri"),
                            chapterIndex = m.getInt("chapter_index").also { require(it >= 0) },
                            chapterName = m.getString("chapter_name"),
                            positionMs = m.getLong("position_ms").also { require(it >= 0) },
                            createdAt = m.getLong("created"),
                            automatic = m.getBoolean("automatic"),
                        )
                    },
                )
            }
            require(books.map { it.uri }.toSet().size == books.size)
            return books
        } catch (e: Exception) {
            throw BackupException(BackupException.Kind.DAMAGED)
        }
    }

    /**
     * Works out what a restore writes, against the library as it is now. A book the library does
     * not have is counted, not kept: there is nowhere to put its place until its folder is added,
     * and the same file can be restored again then.
     */
    fun plan(saved: List<SavedBook>, library: List<LibraryBook>): Plan {
        val byUri = library.associateBy { it.uri }
        val taken = mutableSetOf<String>()
        // Exact addresses first, so a title match never takes a book another saved entry owns.
        val matched = HashMap<SavedBook, LibraryBook>()
        for (s in saved) byUri[s.uri]?.let { matched[s] = it; taken += it.uri }
        for (s in saved) {
            if (s in matched) continue
            val candidates = library.filter {
                it.uri !in taken && same(it.title, s.title) && same(it.author.orEmpty(), s.author.orEmpty())
            }
            // Two books of one name and author cannot be told apart, so neither is guessed at.
            if (candidates.size == 1) { matched[s] = candidates[0]; taken += candidates[0].uri }
        }
        val places = mutableListOf<Place>()
        val bookmarks = LinkedHashMap<String, List<Mark>>()
        var notFound = 0
        for (s in saved) {
            val book = matched[s]
            if (book == null) { notFound++; continue }
            val played = s.lastPlayedAt
            if (s.chapterUri != null && played != null) {
                chapterIn(book, s.chapterUri, s.chapterName, s.chapterIndex)?.let {
                    places += Place(book.uri, it, s.positionMs, played)
                }
            }
            val marks = s.bookmarks.mapNotNull { m ->
                chapterIn(book, m.chapterUri, m.chapterName, m.chapterIndex)?.let {
                    Mark(book.uri, it, m.positionMs, m.createdAt, m.automatic)
                }
            }
            if (marks.isNotEmpty()) bookmarks[book.uri] = marks
        }
        return Plan(places, bookmarks, notFound)
    }

    private fun chapterIn(book: LibraryBook, uri: String, name: String, index: Int): String? {
        book.chapters.firstOrNull { it.uri == uri }?.let { return it.uri }
        book.chapters.filter { name.isNotBlank() && same(it.name, name) }.singleOrNull()?.let { return it.uri }
        return book.chapters.firstOrNull { it.index == index }?.uri
    }

    private fun same(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)
}
