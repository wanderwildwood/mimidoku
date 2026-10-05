package com.wanderwildwood.mimidoku.server

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.wanderwildwood.mimidoku.R
import com.wanderwildwood.mimidoku.data.ChapterEntity
import com.wanderwildwood.mimidoku.data.LibraryDao
import com.wanderwildwood.mimidoku.data.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    private const val CHAPTER_TRIES = 4
    private const val RETRY_WAIT_MS = 3_000L

    /**
     * Where a book goes: the app's own folder on the phone, or its own folder on a memory card.
     *
     * Either way it is the app's folder, so uninstalling takes the audio with it and neither needs
     * a permission. A card the reader chose and has since taken out is not somewhere to put a
     * book, so the phone is used instead rather than failing a download over it.
     */
    private fun folder(context: Context): File? {
        val onCard = if (Preferences.of(context).keepOnCard) card(context) else null
        return (onCard ?: context.getExternalFilesDir(Environment.DIRECTORY_MUSIC))
            ?.resolve("server")
            ?.apply { mkdirs() }
    }

    /**
     * The app's folder on a memory card, if there is a card in the phone.
     *
     * The first of [Context.getExternalFilesDirs] is always the phone's own storage; anything
     * after it is removable. An entry is null, or unmounted, while its card is out.
     */
    fun card(context: Context): File? =
        context.getExternalFilesDirs(Environment.DIRECTORY_MUSIC)
            .drop(1)
            .firstOrNull { it != null && Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }

    private fun safeName(chapterUri: String): String =
        // A chapter's uri is namespaced and carries colons, which not every filesystem will take.
        chapterUri.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".audio"

    /** Where a new download of this chapter would go, given where books are kept now. */
    private fun fileFor(context: Context, chapterUri: String): File? =
        folder(context)?.resolve(safeName(chapterUri))

    /**
     * Where this chapter's audio actually is, which is not necessarily where a new download would
     * go: a book kept before the reader moved downloads to the card stays on the phone, and the
     * row says which. The file named in the row is the one to measure or delete.
     */
    private fun fileOf(chapter: ChapterEntity): File? =
        chapter.audioUri.takeIf { it.startsWith("file:") }?.let { Uri.parse(it).path }?.let(::File)

    /** Whether every chapter of a book is on the phone. Anything less is not a kept book. */
    suspend fun isKept(dao: LibraryDao, bookUri: String): Boolean {
        val chapters = dao.chaptersOf(bookUri)
        return chapters.isNotEmpty() && chapters.all { it.audioUri.isNotEmpty() }
    }

    /**
     * Fetches a whole book, chapter by chapter.
     *
     * Each chapter is written to a temporary name and moved into place only once it is whole, so
     * an interrupted download cannot leave a file that looks playable and is not. A chapter cut
     * off part-way is tried again a few times before the book is given up on: one wifi blip
     * should not cost a whole book, and the reader is not there to press Keep again.
     */
    suspend fun downloadBook(
        context: Context,
        client: AbsClient,
        dao: LibraryDao,
        bookUri: String,
        onProgress: suspend (done: Int, total: Int, fraction: Float) -> Unit = { _, _, _ -> },
    ): AbsResult<Int> {
        val itemId = AbsSync.itemIdOf(bookUri)
            ?: return AbsResult.Failure(context.getString(R.string.download_not_on_server))
        val chapters = dao.chaptersOf(bookUri)
        if (chapters.isEmpty()) return AbsResult.Failure(context.getString(R.string.download_empty))

        var done = 0
        for (chapter in chapters) {
            coroutineContext.ensureActive()
            if (chapter.audioUri.isNotEmpty()) {
                done++
                onProgress(done, chapters.size, 1f)
                continue
            }
            val ino = chapter.uri.substringAfterLast(':')
            var tries = 0
            var r: AbsResult<File>
            while (true) {
                r = download(context, client, itemId, ino, chapter.uri) { fraction ->
                    onProgress(done, chapters.size, fraction)
                }
                // Only a transfer that broke is worth repeating. A server that says no, or a
                // phone with nowhere to put the file, will say the same thing the next time.
                val broke = r is AbsResult.Failure && r.message == context.getString(R.string.download_unfinished)
                if (!broke || ++tries >= CHAPTER_TRIES) break
                delay(RETRY_WAIT_MS * tries)
            }
            when (r) {
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
            ?: return@withContext AbsResult.Failure(context.getString(R.string.download_nowhere))
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
                        context.getString(R.string.download_file_gone)
                    } else {
                        context.getString(R.string.download_server_answered, code)
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
                return@withContext AbsResult.Failure(context.getString(R.string.download_unfinished))
            }
            if (!partial.renameTo(target)) {
                partial.delete()
                return@withContext AbsResult.Failure(context.getString(R.string.download_not_saved))
            }
            AbsResult.Success(target)
        } catch (e: IOException) {
            partial.delete()
            AbsResult.Failure(context.getString(R.string.download_unfinished))
        } finally {
            // A download stopped part-way -- by the reader, or by the phone -- leaves its partial
            // file behind otherwise, and the next try may not even be writing to the same place.
            if (partial.exists()) partial.delete()
            connection?.disconnect()
        }
    }

    /** How much of the phone a kept book is taking up, which is the reason to give one back. */
    suspend fun sizeOf(context: Context, dao: LibraryDao, bookUri: String): Long =
        withContext(Dispatchers.IO) {
            dao.chaptersOf(bookUri).sumOf { chapter ->
                fileOf(chapter)?.takeIf { it.exists() }?.length() ?: 0L
            }
        }

    /**
     * Gives a book back to the server: the audio goes, the row stays, and so does every bookmark
     * and the reader's place in it. Getting it again picks all of that back up.
     */
    suspend fun remove(context: Context, dao: LibraryDao, bookUri: String): Int {
        var removed = 0
        for (chapter in dao.chaptersOf(bookUri)) {
            fileOf(chapter)?.takeIf { it.exists() }?.let {
                if (it.delete()) removed++
            }
            dao.setChapterAudio(chapter.uri, "")
        }
        dao.setKept(bookUri, false)
        return removed
    }
}
