package com.wanderwildwood.mimidoku.playback

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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
import com.wanderwildwood.mimidoku.glance.NowReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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

    /**
     * The reader's place, written here rather than by the screen: see [PlaceKeeper].
     *
     * Writes go through one queue with one reader, so they land in the order they were made: two
     * launched side by side could finish the other way round and leave an older place standing.
     * The queue's reader is not cancelled with [scope], so the last place, written as the service
     * goes, still reaches the database.
     */
    private val keeper = PlaceKeeper()
    private val places = Channel<PlaceKeeper.Place>(Channel.UNLIMITED)
    private var ticking: Job? = null

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
        _activeSession.value = session

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

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (place in places) {
                runCatching {
                    val book = library.bookOfChapter(place.chapterUri) ?: return@runCatching
                    library.rememberPosition(book.uri, place.chapterUri, place.positionMs)
                }.onFailure { Log.w(TAG, "The place was not written", it) }
            }
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) = applyBoost()

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                timer.onPlayingChanged(isPlaying)
                ticking?.cancel()
                ticking = null
                if (isPlaying) {
                    keeper.started()
                    ticking = scope.launch {
                        while (true) {
                            delay(SAVE_EVERY_MS)
                            writePlace(exoPlayer)
                        }
                    }
                } else {
                    // Paused, stopped, played to the end, or waiting on the file: where it stands
                    // now is where the reader will want to come back to.
                    writePlace(exoPlayer)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                when (reason) {
                    // The book is being taken out of the player, for another or for none: this is
                    // the last chance to say where it was. It comes before the item change below.
                    Player.DISCONTINUITY_REASON_REMOVE ->
                        keeper.at(oldPosition.mediaItem?.mediaId, oldPosition.positionMs)?.let(places::trySend)
                    // A chapter ending into the next, or the reader moving: written at once, so a
                    // place moved while paused is not lost with the screen.
                    Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
                    Player.DISCONTINUITY_REASON_SEEK,
                    Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT ->
                        keeper.at(newPosition.mediaItem?.mediaId, newPosition.positionMs)?.let(places::trySend)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) keeper.loaded()
            }

            // Play, pause, a seek, the next file, a stop: Glance's line is worked out again,
            // and Glance is told if it would now say something different.
            override fun onEvents(player: Player, events: Player.Events) = publishReading(player)
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

    /**
     * Hands the place the player is at to the writer, if it is one worth writing. A book played to
     * its end is written at the end of its last file, so it reads as finished.
     */
    private fun writePlace(player: Player) {
        val ended = player.playbackState == Player.STATE_ENDED
        val length = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        val at = if (ended && length != null) length else player.currentPosition
        keeper.at(player.currentMediaItem?.mediaId, at)?.let(places::trySend)
    }

    /** Where the player is, for Glance's lock-screen panel; nothing while stopped or finished. */
    private fun publishReading(player: Player) {
        val chapter = player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }
        val idle = player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED
        NowReading.set(
            this,
            if (chapter == null || idle) {
                null
            } else {
                NowReading.Now(
                    chapterUri = chapter,
                    positionMs = player.currentPosition,
                    durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
                    isPlaying = player.isPlaying,
                    speed = player.playbackParameters.speed,
                    at = NowReading.elapsed(),
                )
            },
        )
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
        _activeSession.value = null
        NowReading.set(this, null)
        // The last place, before the player goes. The writer finishes what is queued and stops.
        player?.let(::writePlace)
        places.close()
        ticking = null
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

        /**
         * The session while the service is alive, for the lock-screen controls, which run in
         * this same process and follow the player directly rather than binding to it.
         */
        private val _activeSession = MutableStateFlow<MediaSession?>(null)
        val activeSession: StateFlow<MediaSession?> = _activeSession

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

        /**
         * How often the place is written while a book plays: often enough that a killed app loses a
         * moment rather than a chapter, rarely enough not to write to storage ten times a minute.
         */
        const val SAVE_EVERY_MS = 5_000L
    }
}
