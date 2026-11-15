package com.wanderwildwood.mimidoku.library

/**
 * The sums behind "how far in" and "how much is left", kept apart from the screens so the same
 * numbers feed the strip in the library and the player, and so they can be tested.
 */
object Progress {

    /**
     * How long the chapter is: the player's word when it holds the book and has read the file,
     * otherwise what the library wrote down. 0 means not known (a stored -1 is a file that could
     * not be read, which is not known either).
     */
    fun chapterLength(playerMs: Long, storedMs: Long): Long =
        if (playerMs > 0) playerMs else storedMs.coerceAtLeast(0)

    /**
     * What is left of the book: the rest of this chapter plus every chapter after it. A chapter
     * whose length is not known yet counts as nothing, so the figure can only grow as lengths
     * come in.
     */
    fun remaining(laterChaptersMs: List<Long>, chapterLengthMs: Long, positionMs: Long): Long =
        laterChaptersMs.sumOf { it.coerceAtLeast(0) } + (chapterLengthMs - positionMs).coerceAtLeast(0)

    /** Where the knob sits, 0 to 1; at the start when the length is not known. */
    fun seekFraction(positionMs: Long, lengthMs: Long): Float =
        if (lengthMs > 0) (positionMs.toFloat() / lengthMs).coerceIn(0f, 1f) else 0f

    /** Whether the reader has been through the whole book: its last few seconds count as the end. */
    fun isFinished(durationMs: Long, progressMs: Long): Boolean =
        durationMs > 0 && progressMs >= durationMs - FINISHED_WITHIN_MS

    /**
     * Where a book stands with its reader. One played to its end is finished rather than still
     * started; played again it is started again, since its place moves back off the end.
     */
    fun status(everPlayed: Boolean, durationMs: Long, progressMs: Long): Status = when {
        !everPlayed -> Status.NotStarted
        isFinished(durationMs, progressMs) -> Status.Finished
        else -> Status.Started
    }

    /** In the order the shelves are listed: the two there always were, then what is done. */
    enum class Status { NotStarted, Started, Finished }

    /**
     * Whether the sleep timer's countdown is redrawn on this half-second look at the player.
     *
     * The place in the book and the countdown each turn over once a second, but not at the same
     * moment, and drawn apart they made two redraws a second on a panel that smears. So the
     * countdown waits for the place to move and both are drawn in one frame. When the place is
     * not moving (paused, or still loading) there is nothing to wait for, and a countdown that
     * has [waited] a second already is drawn anyway.
     */
    fun drawCountdown(placeMoved: Boolean, playing: Boolean, waited: Int): Boolean =
        placeMoved || !playing || waited >= 2

    private const val FINISHED_WITHIN_MS = 3_000L
}
