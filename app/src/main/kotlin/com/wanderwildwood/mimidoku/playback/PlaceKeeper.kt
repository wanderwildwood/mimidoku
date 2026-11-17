package com.wanderwildwood.mimidoku.playback

/**
 * Decides when the reader's place is written down, for the service that holds the player.
 *
 * The place used to be written by the screen, which goes away while a book keeps playing - swiped
 * from Recents, closed with Back, taken back by Android - and the hours listened after that were
 * never written. The service is there for as long as anything plays, so it writes instead, and the
 * screen only draws.
 *
 * Two rules keep this from doing harm. Nothing is written for books that were loaded but never
 * played: a write marks a book as started and makes it the one offered back on opening, and
 * loading a book is not reading it. And the same place is not written twice in a row, so a pause
 * followed by a stop costs one write rather than two.
 */
internal class PlaceKeeper {

    data class Place(val chapterUri: String, val positionMs: Long)

    /** Whether the chapters now in the player have been played since they were handed to it. */
    private var played = false
    private var written: Place? = null

    /** The player has started to sound. */
    fun started() {
        played = true
    }

    /** The player has been handed a different book, or none; its place is not known to be heard. */
    fun loaded() {
        played = false
    }

    /** The place to write for [chapterUri] at [positionMs], or null when there is nothing to write. */
    fun at(chapterUri: String?, positionMs: Long): Place? {
        if (!played || chapterUri.isNullOrEmpty()) return null
        val place = Place(chapterUri, positionMs.coerceAtLeast(0))
        if (place == written) return null
        written = place
        return place
    }
}
