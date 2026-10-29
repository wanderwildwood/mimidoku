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
}
