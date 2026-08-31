package com.wanderwildwood.mimidoku.playback

import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.wanderwildwood.mimidoku.R
import com.wanderwildwood.mimidoku.data.LibraryRepository
import com.wanderwildwood.mimidoku.data.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Holds the player for as long as something is listening.
 *
 * Almost everything a media app has to get right - the notification and its buttons, headset and
 * Bluetooth controls, what happens when a call arrives, what happens when another app wants the
 * speaker - is handled by MediaSession and by asking ExoPlayer to manage audio focus. The job here
 * is to configure those correctly for speech rather than music, and then stay out of the way.
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    /**
     * Held here rather than in the activity because it is attached to the player's audio session,
     * which outlives any screen. Created lazily: allocating it costs an audio effect slot, and a
     * reader who never turns the boost on should not spend one.
     */
    private var boost: LoudnessEnhancer? = null

    /**
     * What the reader last asked for, and the session the effect was built against.
     *
     * The screen sends its saved settings the moment it connects, which is the moment the player
     * was built, and the player does not have an audio session id yet -- it generates one on a
     * background thread. An answer of "not yet" used to be the end of it: the boost was left off
     * until the reader noticed and pressed the tool twice. So the wish is kept, and applied again
     * whenever the session it needs turns up or is replaced.
     */
    private var boostWanted = false
    private var boostSession = C.AUDIO_SESSION_ID_UNSET

    /**
     * The sleep timer runs here, beside the player it stops, rather than on the screen that shows
     * it. A screen is something Android may take back at any time; a book playing is not.
     */
    private var sleep: SleepTimer? = null

    /** For the one thing the timer has to write down: where the reader stopped following. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    // Speech, not music. It changes how the system ducks this against other
                    // audio, and on some devices which volume stream it belongs to.
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                // true: let ExoPlayer request and respond to audio focus, so a call or a
                // navigation prompt pauses the book rather than talking over it.
                true,
            )
            // Pause when the headphones are pulled out, instead of continuing on the speaker.
            .setHandleAudioBecomingNoisy(true)
            // The screen is off for most of what this app is for, and a sleeping processor
            // is a stopped book. It is also what the accelerometer needs to keep reporting,
            // which is how the sleep timer is called off in the dark.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .build()

        player = exoPlayer
        session = MediaSession.Builder(this, exoPlayer).setCallback(Commands()).build()

        val library = LibraryRepository(this)
        val timer = SleepTimer(
            context = this,
            preferences = Preferences.of(this),
            player = exoPlayer,
            onChanged = ::publishSleep,
            onEnded = { chapterUri, positionMs ->
                scope.launch {
                    val book = library.bookOfChapter(chapterUri) ?: return@launch
                    library.addBookmark(book.uri, chapterUri, positionMs, automatic = true)
                }
            },
        )
        sleep = timer
        publishSleep()

        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) = applyBoost()
            override fun onIsPlayingChanged(isPlaying: Boolean) = timer.onPlayingChanged(isPlaying)
        })

        // Media3's own notification icon is a musical note, and it is what the home screen
        // shows beside whatever is playing. A book is not music.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        )
    }

    /**
     * Two things the screen can ask for that a MediaController has no vocabulary for.
     *
     * Skipping silence and boosting loudness are properties of this ExoPlayer and of its audio
     * session, neither of which exists on the far side of the binder, so they are asked for by
     * name instead.
     */
    private inner class Commands : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(SKIP_SILENCE, Bundle.EMPTY))
                        .add(SessionCommand(VOLUME_BOOST, Bundle.EMPTY))
                        .add(SessionCommand(SLEEP_TIMER, Bundle.EMPTY))
                        .build(),
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            command: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val on = args.getBoolean(ON)
            when (command.customAction) {
                SKIP_SILENCE -> player?.skipSilenceEnabled = on
                VOLUME_BOOST -> setBoost(on)
                SLEEP_TIMER -> sleep?.arm(on)
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /**
     * What the screen needs to draw the sleep timer, published rather than asked for.
     *
     * The screen holds none of this. It reads the extras its controller already keeps a copy of,
     * in the same half-second loop it reads the position, so a screen that has just been opened
     * onto a book that has been playing for an hour shows the right clock immediately.
     */
    private fun publishSleep() {
        val timer = sleep ?: return
        session?.setSessionExtras(
            Bundle().apply {
                putBoolean(SLEEP_ARMED, timer.armed)
                putLong(SLEEP_REMAINING, timer.remainingMs)
                putString(SLEEP_EVENT, timer.event)
                putInt(SLEEP_EVENT_ID, timer.eventId)
            }
        )
    }

    private fun setBoost(on: Boolean) {
        boostWanted = on
        applyBoost()
    }

    /**
     * A gain of a few decibels, which is what "boost" is worth for a quietly mastered audiobook.
     * More than this and speech starts to clip rather than get clearer.
     *
     * An effect belongs to one audio session, so a new session means a new effect: the old one
     * would be quietly boosting something that no longer plays. Nothing here is fatal -- a device
     * may simply not offer the effect -- but a failure says so in the log rather than leaving a
     * reader to wonder why the tool does nothing.
     */
    private fun applyBoost() {
        val id = player?.audioSessionId ?: return
        if (id == C.AUDIO_SESSION_ID_UNSET) return
        if (id != boostSession) {
            runCatching { boost?.release() }
            boost = null
            boostSession = id
        }
        if (boost == null) {
            boost = runCatching { LoudnessEnhancer(id) }
                .onFailure { Log.w(TAG, "No loudness enhancer on session $id", it) }
                .getOrNull()
        }
        runCatching {
            boost?.setTargetGain(if (boostWanted) BOOST_MILLIBELS else 0)
            boost?.enabled = boostWanted
        }.onFailure { Log.w(TAG, "Loudness enhancer refused", it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Without this, dismissing the notification while paused leaves the service running with
     * nothing to play.
     */
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val current = player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        sleep?.release()
        sleep = null
        scope.cancel()
        runCatching { boost?.release() }
        boost = null
        boostSession = C.AUDIO_SESSION_ID_UNSET
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlaybackService"

        /** Named commands the screen may send. The Bundle carries a single boolean, [ON]. */
        const val SKIP_SILENCE = "com.wanderwildwood.mimidoku.SKIP_SILENCE"
        const val VOLUME_BOOST = "com.wanderwildwood.mimidoku.VOLUME_BOOST"
        const val SLEEP_TIMER = "com.wanderwildwood.mimidoku.SLEEP_TIMER"
        const val ON = "on"

        /** What the session says about the sleep timer, for the screen to read. */
        const val SLEEP_ARMED = "sleepArmed"
        const val SLEEP_REMAINING = "sleepRemaining"
        const val SLEEP_EVENT = "sleepEvent"
        const val SLEEP_EVENT_ID = "sleepEventId"

        /**
         * Asymmetric on purpose: going back is usually "I missed that", which needs enough to
         * recover the sentence, while going forward is usually skipping something and wants less.
         */
        const val SEEK_BACK_MS = 20_000L
        const val SEEK_FORWARD_MS = 20_000L

        const val BOOST_MILLIBELS = 800
    }
}
