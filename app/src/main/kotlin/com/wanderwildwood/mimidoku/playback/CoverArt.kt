package com.wanderwildwood.mimidoku.playback

import android.content.Context

/**
 * The picture that stands for a book while it is playing.
 *
 * It is not for this app's own screens, which show none: it is what everything outside them asks
 * for - the notification, the lock screen, and the launcher's playback row, which draws a musical
 * note when a session offers no artwork. A book is not music, and the note was the only thing
 * saying otherwise.
 *
 * It is always this app's own mark, never the book's cover: the point of the picture on the home
 * screen is to say which app the sound is coming from. It is read once and kept, because it is
 * the same handful of bytes for every book.
 */
object CoverArt {

    private var mark: ByteArray? = null

    fun mark(context: Context): ByteArray? {
        mark?.let { return it }
        return runCatching { context.assets.open(MARK).use { it.readBytes() } }
            .getOrNull()
            ?.also { mark = it }
    }

    private const val MARK = "cover.png"
}
