package com.wanderwildwood.mimidoku.server

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.wanderwildwood.mimidoku.data.ChapterEntity
import com.wanderwildwood.mimidoku.data.LibraryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Keeps a book from a server on the phone.
 *
 * This is the whole point of the server support rather than a convenience on top of it. A book is
 * ten hours of audio and this is an e-ink phone: streaming one is hours of radio for something
 * that could have been fetched once over the wifi at home. So there is no streaming here. A
 * server book is a book you can *get*, and until you get it there is nothing to play.
 *
 * What a download changes is one field. [ChapterEntity.audioUri] stops being empty and becomes a
 * file, and that is the entire difference between a book on the server and a book on the phone --
 * the row is the same row, and every bookmark and every remembered position in it survives,
 * because none of them ever pointed at the audio.
 */
object AbsDownloader {

    /** Kept in the app's own folder, so uninstalling takes the audio with it. */
    private fun folder(context: Context): File? =
        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?.resolve("server")
            ?.apply { mkdirs() }

    fun fileFor(context: Context, chapterUri: String): File? {
        // A chapter's uri is namespaced and carries colons, which not every filesystem will take.
        val safe = chapterUri.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return folder(context)?.resolve("$safe.audio")
    }

    /** Whether every chapter of a book is on the phone. Anything less is not a kept book. */
    suspend fun isKept(dao: LibraryDao, bookUri: String): Boolean {
        val chapters = dao.chaptersOf(bookUri)
        return chapters.isNotEmpty() && chapters.all { it.audioUri.isNotEmpty() }
    }

    /**
     * Fetches a whole book, chapter by chapter.
     *
     * Each chapter is written to a temporary name and moved into place only once it is whole, so
     * an interrupted download cannot leave a file that looks playable and is not. A chapter that
     * is already here is skipped, which is what makes a cancelled download worth resuming rather
     * than starting again.
     */
    suspend fun downloadBook(
        context: Context,
        client: AbsClient,
        dao: LibraryDao,
        bookUri: String,
        onProgress: suspend (done: Int, total: Int, fraction: Float) -> Unit = { _, _, _ -> },
    ): AbsResult<Int> {
        val itemId = AbsSync.itemIdOf(bookUri)
            ?: return AbsResult.Failure("That book is not on a server.")
        val chapters = dao.chaptersOf(bookUri)
        if (chapters.isEmpty()) return AbsResult.Failure("That book has nothing in it.")

        var done = 0
        for (chapter in chapters) {
            coroutineContext.ensureActive()
            if (chapter.audioUri.isNotEmpty()) {
                done++
                onProgress(done, chapters.size, 1f)
                continue
            }
            val ino = chapter.uri.substringAfterLast(':')
            when (val r = download(context, client, itemId, ino, chapter.uri) { fraction ->
                onProgress(done, chapters.size, fraction)
            }) {
                is AbsResult.Failure -> return r
                is AbsResult.Success -> {
                    // Written only once the file is whole and in place. A row that says a file is
                    // here when it is not would be a book that plays silence.
                    dao.setChapterAudio(chapter.uri, Uri.fromFile(r.value).toString())
                    done++
                    onProgress(done, chapters.size, 1f)
                }
            }
        }
        // Said only once every chapter is here. A book that is half fetched is not a book you
        // can sit down with, and a shelf that claimed otherwise would be lying by a few hours.
        if (done == chapters.size) dao.setKept(bookUri, true)
        return AbsResult.Success(done)
    }

    private suspend fun download(
        context: Context,
        client: AbsClient,
        itemId: String,
        ino: String,
        chapterUri: String,
        onProgress: suspend (Float) -> Unit,
    ): AbsResult<File> = withContext(Dispatchers.IO) {
        val target = fileFor(context, chapterUri)
            ?: return@withContext AbsResult.Failure("There is nowhere on this phone to put it.")
        if (target.exists() && target.length() > 0) return@withContext AbsResult.Success(target)

        val partial = File(target.absolutePath + ".part")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(client.fileUrl(itemId, ino)).openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    // Generous, because this is a large file over a home network, and finite,
                    // because a stalled socket must not hold the download open for ever.
                    readTimeout = 120_000
                    val (name, value) = client.authHeader()
                    setRequestProperty(name, value)
                }
            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext AbsResult.Failure(
                    if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                        // The server lists books whose files it can no longer find. It says so in
                        // the listing, and those are skipped, but a file can go missing between
                        // the sync and the download.
                        "The server no longer has that file."
                    } else {
                        "The server answered $code."
                    },
                )
            }
            val total = connection.contentLengthLong
            var written = 0L
            var lastPercent = -1
            partial.outputStream().use { out ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            // A whole percent at a time; this panel cannot show more.
                            val percent = ((written * 100) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            if (total > 0 && written < total) {
                // A truncated file is the failure this whole dance exists to catch: it opens, it
                // plays, and it stops early, which is indistinguishable from a bad recording.
                partial.delete()
                return@withContext AbsResult.Failure("That chapter did not finish downloading.")
            }
            if (!partial.renameTo(target)) {
                partial.delete()
                return@withContext AbsResult.Failure("The download could not be saved.")
            }
            AbsResult.Success(target)
        } catch (e: IOException) {
            partial.delete()
            AbsResult.Failure("That chapter did not finish downloading.")
        } finally {
            connection?.disconnect()
        }
    }

    /** How much of the phone a kept book is taking up, which is the reason to give one back. */
    suspend fun sizeOf(context: Context, dao: LibraryDao, bookUri: String): Long =
        withContext(Dispatchers.IO) {
            dao.chaptersOf(bookUri).sumOf { chapter ->
                fileFor(context, chapter.uri)?.takeIf { it.exists() }?.length() ?: 0L
            }
        }

    /**
     * Gives a book back to the server: the audio goes, the row stays, and so does every bookmark
     * and the reader's place in it. Getting it again picks all of that back up.
     */
    suspend fun remove(context: Context, dao: LibraryDao, bookUri: String): Int {
        var removed = 0
        for (chapter in dao.chaptersOf(bookUri)) {
            fileFor(context, chapter.uri)?.takeIf { it.exists() }?.let {
                if (it.delete()) removed++
            }
            dao.setChapterAudio(chapter.uri, "")
        }
        dao.setKept(bookUri, false)
        return removed
    }
}
