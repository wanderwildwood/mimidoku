package com.wanderwildwood.mimidoku.playback

import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.wanderwildwood.mimidoku.R

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
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .build()

        player = exoPlayer
        session = MediaSession.Builder(this, exoPlayer).setCallback(Commands()).build()

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
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /**
     * A gain of a few decibels, which is what "boost" is worth for a quietly mastered audiobook.
     * More than this and speech starts to clip rather than get clearer.
     */
    private fun setBoost(on: Boolean) {
        val id = player?.audioSessionId ?: return
        if (boost == null && id != C.AUDIO_SESSION_ID_UNSET) {
            boost = runCatching { LoudnessEnhancer(id) }.getOrNull()
        }
        runCatching {
            boost?.setTargetGain(if (on) BOOST_MILLIBELS else 0)
            boost?.enabled = on
        }
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
        runCatching { boost?.release() }
        boost = null
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }

    companion object {
        /** Named commands the screen may send. The Bundle carries a single boolean, [ON]. */
        const val SKIP_SILENCE = "com.wanderwildwood.mimidoku.SKIP_SILENCE"
        const val VOLUME_BOOST = "com.wanderwildwood.mimidoku.VOLUME_BOOST"
        const val ON = "on"

        /**
         * Asymmetric on purpose: going back is usually "I missed that", which needs enough to
         * recover the sentence, while going forward is usually skipping something and wants less.
         */
        const val SEEK_BACK_MS = 20_000L
        const val SEEK_FORWARD_MS = 20_000L

        const val BOOST_MILLIBELS = 800
    }
}
