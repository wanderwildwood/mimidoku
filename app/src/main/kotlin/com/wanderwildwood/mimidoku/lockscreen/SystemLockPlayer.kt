package com.wanderwildwood.mimidoku.lockscreen

import android.os.Build

/**
 * Whether the phone's own lock screen already has a player for every app.
 *
 * MuditaOS 1.x's lock-screen music widget is wired to Mudita's player by name and shows
 * nothing for any other, so there this app draws its own strip. From MuditaOS 2.0 that widget
 * shows whatever app is playing, and KompaktOS and other Android lock screens do the same, so
 * this app's strip would only stack a second player under the phone's own.
 */
object SystemLockPlayer {
    fun present(): Boolean = presentFor(Build.DISPLAY.orEmpty())

    /** [display] is the build's display id, e.g. "MuditaOS K 1.6.1". */
    internal fun presentFor(display: String): Boolean {
        val major = MUDITA.find(display)?.groupValues?.get(1)?.toIntOrNull() ?: return true
        return major >= 2
    }

    private val MUDITA = Regex("^MuditaOS\\b\\D*(\\d+)\\.")
}
