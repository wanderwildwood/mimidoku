package com.wanderwildwood.mimidoku.glance

import android.content.Context
import android.os.SystemClock

/**
 * Where the player is, as it last said, for Glance's lock-screen panel.
 *
 * Written by [com.wanderwildwood.mimidoku.playback.PlaybackService] on the player's thread when
 * something changes - a play, a pause, a seek, the next file - and read by [ReadingOnLockScreen]
 * on whatever thread Glance's question arrives on. The position between those moments is worked
 * out from the clock rather than written down every second. Null whenever nothing is loaded.
 */
object NowReading {

    data class Now(
        /** The chapter's id, which is what the library knows it by. */
        val chapterUri: String,
        val positionMs: Long,
        /** The player's length for this file, or 0 while it does not know. */
        val durationMs: Long,
        val isPlaying: Boolean,
        val speed: Float,
        /** [SystemClock.elapsedRealtime] when [positionMs] was read. */
        val at: Long,
    ) {
        /** How far into the file now, carried forward from when it was last read. */
        fun positionAt(now: Long): Long =
            if (isPlaying) positionMs + ((now - at) * speed).toLong() else positionMs
    }

    @Volatile
    var current: Now? = null
        private set

    /** Tells Glance only when the reading has changed in a way the clock would not have. */
    fun set(context: Context, now: Now?) {
        val was = current
        current = now
        val same = was?.chapterUri == now?.chapterUri && was?.isPlaying == now?.isPlaying &&
            (was == null || now == null || kotlin.math.abs(was.positionAt(now.at) - now.positionMs) < 2_000)
        if (!same) GlanceProvider.changed(context)
    }

    fun elapsed(): Long = SystemClock.elapsedRealtime()

    /**
     * Which part of a book the reader is in, counted from 1: the last part that starts at or before
     * [positionMs] in [chapterUri]. A part is a file, or a mark inside one, as `(chapter, start)`.
     * Null when the chapter is not among them.
     */
    fun partNumber(parts: List<Pair<String, Long>>, chapterUri: String, positionMs: Long): Int? {
        val last = parts.indexOfLast { it.first == chapterUri && it.second <= positionMs }
        if (last >= 0) return last + 1
        val first = parts.indexOfFirst { it.first == chapterUri }
        return if (first >= 0) first + 1 else null
    }

    /** A length as the player writes it: "1:05:09", or "5:09" under an hour. */
    fun clock(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        fun two(n: Long) = n.toString().padStart(2, '0')
        return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
    }
}
