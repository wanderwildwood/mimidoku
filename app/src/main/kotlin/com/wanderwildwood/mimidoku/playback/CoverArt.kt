package com.wanderwildwood.mimidoku.playback

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The picture that stands for a book while it is playing.
 *
 * It is not for this app's own screens, which show none: it is what everything outside them asks
 * for - the notification, the lock screen, and the launcher's playback row, which draws a musical
 * note when a session offers no artwork. A book is not music, and the note was the only thing
 * saying otherwise.
 *
 * A cover of the book's own is always preferred. The app's mark is the fallback, and it is read
 * once and kept, because it is the same handful of bytes for every book that has nothing better.
 */
object CoverArt {

    private var mark: ByteArray? = null

    /**
     * Reads the first file of the book, which is where an embedded cover lives if there is one.
     * Only the first: the alternative is opening every chapter of a forty-part book to find the
     * same picture forty times, and a book whose first file has no cover does not have one.
     */
    suspend fun forBook(context: Context, firstChapterUri: String?): ByteArray? =
        withContext(Dispatchers.IO) {
            embedded(context, firstChapterUri) ?: mark(context)
        }

    private fun embedded(context: Context, uri: String?): ByteArray? {
        if (uri == null) return null
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri.toUri())
                retriever.embeddedPicture
            }
        }.getOrNull()
    }

    private fun mark(context: Context): ByteArray? {
        mark?.let { return it }
        return runCatching { context.assets.open(MARK).use { it.readBytes() } }
            .getOrNull()
            ?.also { mark = it }
    }

    private const val MARK = "cover.png"
}
