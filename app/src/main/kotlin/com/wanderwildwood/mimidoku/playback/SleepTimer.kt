package com.wanderwildwood.mimidoku.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import com.wanderwildwood.mimidoku.data.Preferences
import com.wanderwildwood.mimidoku.data.Shake
import java.util.Calendar

/**
 * The sleep timer, which belongs to the player rather than to the screen.
 *
 * It lived in the screen once, and a reader who looked at a message while a book played came back
 * to a timer that had quietly gone: Android is free to take an activity that is no longer in front
 * of anyone, and the countdown went with it while the book played on. Nothing here depends on
 * anything being on screen. The service holds this for as long as it holds the player, and the
 * screen only reads it.
 *
 * Being armed is a setting, not an event. A reader who wants a timer tonight wanted one last night
 * and will want one tomorrow, so it survives the app being closed and the timer running out — what
 * resets is the clock, not the wish.
 */
class SleepTimer(
    context: Context,
    private val preferences: Preferences,
    private val player: Player,
    /** Something changed that the screen would want to show. */
    private val onChanged: () -> Unit,
    /** Where the reader stopped following, for the automatic bookmark. */
    private val onEnded: (chapterUri: String, positionMs: Long) -> Unit,
) {

    /** On or off. Restored from what the reader last chose, whenever that was. */
    var armed: Boolean = preferences.sleepArmed
        private set

    /** What is left on the clock. Full and waiting when armed but not counting. */
    var remainingMs: Long = if (preferences.sleepArmed) fullMs() else 0L
        private set

    /**
     * The last thing worth saying out loud, and a number that changes when it is said again.
     *
     * The screen cannot tell one "Sleep timer restarted" from the next by its text, so it is given
     * something that differs.
     */
    var event: String? = null
        private set
    var eventId: Int = 0
        private set

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable { onTick() }
    private var shakes: ShakeDetector? = null

    /** Where the wind-down began, which is the place the reader last heard. */
    private var followedChapter: String? = null
    private var followedPosition = 0L

    init {
        listenForShakes()
        schedule()
    }

    /** The reader pressing the tool, and the only way it is turned off. */
    fun arm(on: Boolean) {
        set(on, if (on) "Sleep timer on" else "Sleep timer off")
    }

    /**
     * Playback starting or stopping.
     *
     * The clock only runs against a book that is playing: a timer that ran down in a pocket and
     * then paused a book that was already stopped would be no use to anyone. This is also where
     * the timer lets itself on, if the reader has asked for that and it is late enough — the
     * moment a book starts, rather than on a clock of its own, because a window that armed the
     * timer at ten sharp would arm it while the phone sat on a shelf.
     */
    fun onPlayingChanged(isPlaying: Boolean) {
        if (isPlaying && !armed && preferences.autoSleep &&
            withinWindow(preferences.autoSleepStart, preferences.autoSleepEnd)
        ) {
            set(true, "Sleep timer on")
            return
        }
        listenForShakes()
        schedule()
    }

    fun release() {
        handler.removeCallbacks(tick)
        shakes?.stop()
        shakes = null
    }

    private fun set(on: Boolean, why: String?) {
        armed = on
        preferences.sleepArmed = on
        remainingMs = if (on) fullMs() else 0L
        followedChapter = null
        restoreVolume()
        listenForShakes()
        schedule()
        say(why)
        onChanged()
    }

    private fun onTick() {
        if (!armed) return
        if (!player.isPlaying) {
            schedule()
            return
        }

        val left = remainingMs - 1_000
        if (left > 0) {
            // The place to mark is where the sound began to go, not where it stopped: the last
            // minute is a fade, and a reader who slept through it never heard those words.
            if (left <= WIND_DOWN_MS && followedChapter == null) {
                followedChapter = player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }
                followedPosition = player.currentPosition
            }
            remainingMs = left
            player.volume = windDownVolume(left)
            onChanged()
            schedule()
        } else {
            end()
        }
    }

    private fun end() {
        val chapter = followedChapter
            ?: player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }
        val at = if (followedChapter != null) followedPosition else player.currentPosition

        player.pause()
        restoreVolume()

        // Still armed: the clock is what ran out, not the reader's mind about wanting one. It goes
        // back to full, ready for whenever the book is started again.
        remainingMs = fullMs()
        followedChapter = null
        if (chapter != null) onEnded(chapter, at)
        say("Sleep timer ended")
        listenForShakes()
        schedule()
        onChanged()
    }

    /**
     * A shake puts the clock back to full, so a reader who is still awake when the sound starts
     * going can say so without finding a button in the dark.
     */
    private fun onShake() {
        if (!armed) return
        remainingMs = fullMs()
        followedChapter = null
        restoreVolume()
        say("Sleep timer restarted")
        onChanged()
    }

    private fun schedule() {
        handler.removeCallbacks(tick)
        if (armed) handler.postDelayed(tick, 1_000)
    }

    /** Listening for a shake costs battery, so it happens only while there is a clock to reset. */
    private fun listenForShakes() {
        val wanted = armed && player.isPlaying && preferences.shake != Shake.Off
        if (wanted == (shakes != null)) return
        if (wanted) {
            shakes = ShakeDetector(appContext, preferences.shake.threshold) { onShake() }
                .also { it.start() }
        } else {
            shakes?.stop()
            shakes = null
        }
    }

    private fun restoreVolume() {
        if (player.volume != 1f) player.volume = 1f
    }

    private fun say(what: String?) {
        event = what
        eventId++
    }

    private fun fullMs(): Long = preferences.sleepMinutes * 60_000L

    private companion object {

        /** The tail of the timer, spent fading out. Shorter than the shortest timer offered. */
        const val WIND_DOWN_MS = 60_000L

        /**
         * How loud the book should be with [remaining] left, full volume being 1.
         *
         * Nothing happens until the last minute, and then it goes down to nothing across it. The
         * curve is squared because amplitude is not loudness: a fade that is straight in amplitude
         * sounds like it hurries at the beginning and then hangs about at the end, where squaring
         * it falls away evenly to the ear. The last few seconds are inaudible, which is the point
         * of them.
         */
        fun windDownVolume(remaining: Long): Float {
            if (remaining >= WIND_DOWN_MS) return 1f
            val fraction = (remaining.toFloat() / WIND_DOWN_MS).coerceIn(0f, 1f)
            return fraction * fraction
        }

        /**
         * Whether the clock is now inside the nightly window, both ends given in minutes since
         * midnight.
         *
         * The window almost always crosses midnight, which is why this is not a comparison. Ten at
         * night until six in the morning is `start > end`, and the hours inside it are the ones at
         * or after start *or* before end -- the opposite of what reading the two numbers in order
         * suggests. A window whose ends are the same hour is no window, and never fires.
         */
        fun withinWindow(startMinutes: Int, endMinutes: Int): Boolean {
            val now = Calendar.getInstance()
            val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            return if (startMinutes <= endMinutes) {
                minutes >= startMinutes && minutes < endMinutes
            } else {
                minutes >= startMinutes || minutes < endMinutes
            }
        }
    }
}
