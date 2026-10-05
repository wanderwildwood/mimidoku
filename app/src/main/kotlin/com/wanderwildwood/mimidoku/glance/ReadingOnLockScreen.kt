package com.wanderwildwood.mimidoku.glance

import android.content.Context
import com.wanderwildwood.mimidoku.R
import com.wanderwildwood.mimidoku.data.LibraryDatabase
import com.wanderwildwood.mimidoku.data.Preferences
import kotlinx.coroutines.runBlocking

/**
 * The book being read or paused, for Glance's lock-screen panel: "Book · Chapter 4", or, for a
 * book that is one file with no chapters inside it, how long is left of it. Nothing at all
 * while nothing is loaded.
 */
class ReadingOnLockScreen : GlanceProvider() {

    override fun enabled(context: Context): Boolean = Preferences.of(context).readingOnLockScreen

    override fun lines(context: Context): List<GlanceProvider.Line> {
        val now = NowReading.current ?: return emptyList()
        val dao = LibraryDatabase.get(context).dao()
        // Glance asks on a binder thread of its own, which can wait for three small reads.
        return runBlocking {
            val book = dao.bookOfChapter(now.chapterUri) ?: return@runBlocking emptyList()
            val chapters = dao.chaptersOf(book.uri)
            val marks = dao.marksOf(book.uri)
            // A part is a file, or a mark inside one, the way the player screen counts them.
            val parts = if (marks.isNotEmpty()) {
                marks.map { it.chapterUri to it.startMs }
            } else {
                chapters.map { it.uri to 0L }
            }
            val position = now.positionAt(NowReading.elapsed())
            val title = book.tagTitle ?: book.name
            val text = if (parts.size > 1) {
                NowReading.partNumber(parts, now.chapterUri, position)
                    ?.let { context.getString(R.string.glance_chapter, title, it) }
                    ?: title
            } else {
                val length = now.durationMs.takeIf { it > 0 }
                    ?: chapters.firstOrNull { it.uri == now.chapterUri }?.durationMs?.takeIf { it > 0 }
                if (length != null) {
                    context.getString(R.string.glance_left, title, NowReading.clock(length - position))
                } else {
                    title
                }
            }
            val lead = context.getString(if (now.isPlaying) R.string.glance_playing else R.string.glance_paused)
            listOf(GlanceProvider.Line(text = text, lead = lead))
        }
    }
}
