package com.wanderwildwood.mimidoku.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.wanderwildwood.mimidoku.library.PlacesBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Places backup and restore against a file the reader picked: the Android half of [PlacesBackup]. */
object PlacesFiles {

    sealed interface Result {
        data class Done(val books: Int, val notFound: Int) : Result
        data class Refused(val kind: PlacesBackup.BackupException.Kind) : Result
        data object Unreadable : Result
    }

    /** Writes the backup to [uri]; false, with the file removed if it was left empty, when that fails. */
    suspend fun save(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val dao = LibraryDatabase.get(context).dao()
        val marksOf = dao.allBookmarks().groupBy { it.bookUri }
        val saved = savedOf(dao, dao.allBooks().filter { it.lastPlayedAt != null || marksOf[it.uri].orEmpty().isNotEmpty() })
        val text = PlacesBackup.write(saved)
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("no stream")
        }.isSuccess
        if (!ok) runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        ok
    }

    /**
     * What carrying [from]'s places and bookmarks over to [to] would write, matched the way a
     * restore matches (title and author, then chapter): for a folder chosen again at a new address.
     * Every book of [from] is in it, read or not, so [PlacesBackup.Plan.notFound] says how many of
     * them [to] does not have.
     */
    suspend fun carryPlan(context: Context, from: Collection<String>, to: Collection<String>): PlacesBackup.Plan =
        withContext(Dispatchers.IO) {
            val dao = LibraryDatabase.get(context).dao()
            val books = dao.allBooks()
            val chaptersOf = dao.allChapters().groupBy { it.bookUri }
            val target = to.toSet()
            val library = books.filter { it.uri in target }.map { b ->
                PlacesBackup.LibraryBook(
                    uri = b.uri,
                    title = titleOf(b),
                    author = authorOf(b),
                    chapters = chaptersOf[b.uri].orEmpty().map { PlacesBackup.LibraryChapter(it.uri, it.sortIndex, it.name) },
                )
            }
            val source = from.toSet()
            PlacesBackup.plan(savedOf(dao, books.filter { it.uri in source }), library)
        }

    /** [books] as a backup writes them: where each was, and its bookmarks. */
    private suspend fun savedOf(dao: LibraryDao, books: List<BookEntity>): List<PlacesBackup.SavedBook> {
        val chapterOf = dao.allChapters().associateBy { it.uri }
        val marksOf = dao.allBookmarks().groupBy { it.bookUri }
        return books
            .map { b ->
                val current = b.currentChapterUri?.let { chapterOf[it] }
                PlacesBackup.SavedBook(
                    uri = b.uri,
                    title = titleOf(b),
                    author = authorOf(b),
                    chapterUri = current?.uri,
                    chapterIndex = current?.sortIndex ?: 0,
                    chapterName = current?.name.orEmpty(),
                    positionMs = b.positionMs.coerceAtLeast(0),
                    lastPlayedAt = b.lastPlayedAt,
                    bookmarks = marksOf[b.uri].orEmpty().mapNotNull { m ->
                        val c = chapterOf[m.chapterUri] ?: return@mapNotNull null
                        PlacesBackup.SavedBookmark(c.uri, c.sortIndex, c.name, m.positionMs.coerceAtLeast(0), m.createdAt, m.automatic)
                    },
                )
            }
    }

    /**
     * Reads the whole file first; only a file that passes changes anything, and the library takes
     * it in one transaction. A file over 20 MB is refused unread: years of bookmarks are far smaller.
     */
    suspend fun restore(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { readCapped(it) }
        }.getOrNull() ?: return@withContext Result.Unreadable
        if (bytes.size > MAX_BYTES) return@withContext Result.Refused(PlacesBackup.BackupException.Kind.NOT_A_BACKUP)
        val saved = try {
            PlacesBackup.read(String(bytes, Charsets.UTF_8))
        } catch (e: PlacesBackup.BackupException) {
            return@withContext Result.Refused(e.kind)
        }
        val dao = LibraryDatabase.get(context).dao()
        val chaptersOf = dao.allChapters().groupBy { it.bookUri }
        val library = dao.allBooks().map { b ->
            PlacesBackup.LibraryBook(
                uri = b.uri,
                title = titleOf(b),
                author = authorOf(b),
                chapters = chaptersOf[b.uri].orEmpty().map { PlacesBackup.LibraryChapter(it.uri, it.sortIndex, it.name) },
            )
        }
        val plan = PlacesBackup.plan(saved, library)
        runCatching { dao.restorePlaces(plan.places, plan.bookmarks) }.onFailure { return@withContext Result.Unreadable }
        Result.Done(plan.books, plan.notFound)
    }

    /** The name a book goes by on both phones: the tag when the files carry one, else the folder. */
    private fun titleOf(b: BookEntity) = b.tagTitle?.takeIf { it.isNotBlank() } ?: b.name
    private fun authorOf(b: BookEntity) = b.author ?: b.tagAuthor

    /** At most MAX_BYTES + 1 bytes, so a file that is too big can be told from one that just fits. */
    private fun readCapped(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() <= MAX_BYTES) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private const val MAX_BYTES = 20 * 1024 * 1024
}
