package com.wanderwildwood.mimidoku.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.wanderwildwood.mimidoku.data.BookEntity
import com.wanderwildwood.mimidoku.data.ChapterEntity
import com.wanderwildwood.mimidoku.data.DurationReader
import com.wanderwildwood.mimidoku.data.LibraryDatabase
import com.wanderwildwood.mimidoku.data.SOURCE_OPENED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * An audiobook file handed over by another app - "Open with" from a file manager, a download -
 * read as a book of its own: one file, with the chapters inside it where it has them.
 *
 * It is a row like any other book, keyed by the address it was handed over by, so opening the
 * same file again picks up where the reader stopped. It is not put on a shelf. The library is
 * the folders the reader granted, and a file shared by another app can usually be read only
 * until this app closes; a shelf of books that will not open would be worse than none.
 */
object OpenedBook {

    /**
     * The book, ready to play: the one already known by this address, or a new one measured
     * and read for its chapter marks before it is handed back. Null when the file cannot be
     * opened at all.
     */
    suspend fun open(context: Context, uri: Uri): BookEntity? = withContext(Dispatchers.IO) {
        if (!readable(context, uri)) return@withContext null
        val dao = LibraryDatabase.get(context).dao()
        val key = uri.toString()
        val known = dao.book(key)
        if (known == null) {
            val file = displayName(context, uri) ?: uri.lastPathSegment.orEmpty()
            dao.insertBooks(
                listOf(
                    BookEntity(
                        uri = key,
                        name = bookName(file),
                        author = null,
                        chapterCount = 1,
                        durationMs = 0L,
                        genre = null,
                        tagTitle = null,
                        tagAuthor = null,
                        currentChapterUri = null,
                        positionMs = 0L,
                        progressMs = 0L,
                        lastPlayedAt = null,
                        sourceType = SOURCE_OPENED,
                        kept = true,
                        seenAt = System.currentTimeMillis(),
                    ),
                ),
            )
            dao.insertChapters(
                listOf(ChapterEntity(uri = key, bookUri = key, name = file, sortIndex = 0, durationMs = 0L, audioUri = key)),
            )
        }
        // Measured now rather than by the background pass, which reads only the granted
        // folders' books. Again for a known one that could not be read last time.
        dao.chaptersOf(key).filter { it.durationMs <= 0 }.forEach { DurationReader(context).now(it) }
        dao.book(key)
    }

    /** The file's name without its extension, which is what a book delivered as one file is called until its tags say. */
    fun bookName(fileName: String): String {
        val name = fileName.substringAfterLast('/').trim()
        val bare = if (name.lastIndexOf('.') > 0) name.substringBeforeLast('.') else name
        return bare.ifEmpty { name }
    }

    /**
     * Whether being refused this file could be put right by allowing access to audio files: a
     * file named by its path, or one of the phone's own media entries, rather than one another
     * app shared (whose permission comes with it).
     */
    fun needsMediaAccess(uri: Uri): Boolean =
        uri.scheme == "file" || (uri.scheme == "content" && uri.authority == "media")

    /**
     * Whether a file another app names may be opened at all. Only a file or a document, and
     * never one of this app's own: what is read here is read as this app, so a file under its
     * private folders, or one of its own providers, would be opened for an app that could not
     * open it itself.
     */
    fun mayOpen(context: Context, uri: Uri): Boolean = when (uri.scheme) {
        "content" -> !isOwnAuthority(uri.authority, context.packageName)
        "file" -> {
            val path = uri.path
            if (path.isNullOrEmpty()) {
                false
            } else {
                val own = buildList {
                    add(context.dataDir.path)
                    add(context.createDeviceProtectedStorageContext().dataDir.path)
                    // Android/data/<app> and Android/obb/<app>, on the phone and on a card.
                    context.getExternalFilesDirs(null).filterNotNull().forEach { dir -> dir.parentFile?.let { add(it.path) } }
                    context.obbDirs.filterNotNull().forEach { add(it.path) }
                }
                !insideAny(canonical(path), own.map(::canonical))
            }
        }
        else -> false
    }

    /** This app's own provider: its package name, or a name under it. */
    fun isOwnAuthority(authority: String?, packageName: String): Boolean =
        authority != null && listOf(packageName, APP_ID).any { authority == it || authority.startsWith("$it.") }

    /** Whether [path] is one of [dirs] or anywhere under one. */
    fun insideAny(path: String, dirs: List<String>): Boolean = dirs.any { dir ->
        val d = dir.trimEnd('/')
        d.isNotEmpty() && (path == d || path.startsWith("$d/"))
    }

    /** With "..", and links such as /data/user/0 for /data/data, worked out. */
    private fun canonical(path: String): String = runCatching { File(path).canonicalPath }.getOrDefault(path)

    /** The providers in the manifest are named after the release id, whatever this build is called. */
    private const val APP_ID = "com.wanderwildwood.mimidoku"

    fun readable(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    private fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }
}
