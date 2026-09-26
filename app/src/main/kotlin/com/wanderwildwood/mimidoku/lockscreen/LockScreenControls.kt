package com.wanderwildwood.mimidoku.lockscreen

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.wanderwildwood.mimidoku.MainActivity
import com.wanderwildwood.mimidoku.playback.PlaybackService
import com.wanderwildwood.mimidoku.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * This app's controls on the Kompakt's lock screen. Optional, and off until the reader turns
 * this service on in Android's Accessibility settings.
 *
 * The same file is in Music Box and in Audio Reading, differing only in its package, the app
 * it follows and the words; the strip is meant to look alike in both, and alike to the one
 * inkOS draws at the foot of its home screen.
 *
 * The Kompakt's own lock screen has a music widget, but it is wired to Mudita's player by name
 * and shows nothing for any other. This draws one for what this app is playing and nothing
 * else. It has to be an accessibility service because only one may
 * put a window above the lock screen; it listens to the system UI alone - which is the lock
 * screen - and reads one thing there, whether the PIN field is focused, so the controls can
 * stand aside for it. It reads no other app and needs no notification access: the player it
 * shows is this app's own, followed in-process.
 *
 * The approach is gezimos's, from the Katapult launcher (GPL-3.0), whose lock-screen widget
 * works this way and does it for every player; with Katapult's service on, this one stands
 * aside so the two never stack.
 */
class LockScreenControls : AccessibilityService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private data class Now(val title: String, val artist: String, val isPlaying: Boolean, val art: ByteArray?)

    private val handler = Handler(Looper.getMainLooper())
    private val now = mutableStateOf<Now?>(null)
    private var scope: CoroutineScope? = null
    private var session: MediaSession? = null
    private var overlay: ComposeView? = null

    /** Set by Stop, which pauses and puts the controls away until something plays again. */
    private var putAway = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = evaluate()
    }

    // Events come only from the system UI, so the last one on unlocking is the lock screen
    // leaving while it still reports itself locked. While the controls are up they look again
    // on a short tick, so they cannot be left standing over the home screen.
    private val recheck = Runnable { evaluate() }

    override fun onServiceConnected() {
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob()).also { s ->
            s.launch {
                PlaybackService.activeSession.collect { attach(it) }
            }
        }
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        evaluate()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = evaluate()

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(recheck)
        attach(null)
        scope?.cancel()
        scope = null
        remove()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    private fun attach(newSession: MediaSession?) {
        session?.player?.removeListener(listener)
        session = newSession
        newSession?.player?.addListener(listener)
        refresh()
    }

    private fun refresh() {
        val player = session?.player
        if (player == null || player.currentMediaItem == null || player.playbackState == Player.STATE_IDLE) {
            now.value = null
        } else {
            if (player.isPlaying) putAway = false
            val meta = player.mediaMetadata
            now.value = Now(
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                isPlaying = player.isPlaying,
                art = meta.artworkData,
            )
        }
        evaluate()
    }

    private fun evaluate() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        val show = keyguard.isKeyguardLocked &&
            power.isInteractive &&
            now.value != null &&
            !putAway &&
            !katapultShowsIt() &&
            !inCall() &&
            !pinShowing()
        if (show) add() else remove()
        handler.removeCallbacks(recheck)
        if (overlay != null) handler.postDelayed(recheck, RECHECK_MS)
    }

    private fun add() {
        if (overlay != null) return
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LockScreenControls)
            setViewTreeViewModelStoreOwner(this@LockScreenControls)
            setViewTreeSavedStateRegistryOwner(this@LockScreenControls)
            setContent {
                val playing = now.value ?: return@setContent
                ControlsPanel(
                    title = playing.title,
                    artist = playing.artist,
                    isPlaying = playing.isPlaying,
                    art = playing.art,
                    ink = if (night) Color.White else Color.Black,
                    surface = if (night) Color.Black else Color.White,
                    onOpen = ::openApp,
                    onPrevious = { session?.player?.seekToPrevious() },
                    onPlayPause = {
                        session?.player?.let { if (it.isPlaying) it.pause() else it.play() }
                    },
                    onNext = { session?.player?.seekToNext() },
                    onStop = {
                        session?.player?.pause()
                        putAway = true
                        evaluate()
                    },
                )
            }
        }
        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = resources.displayMetrics.widthPixels - (2 * SIDE_MARGIN_DP * density).toInt()
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (BOTTOM_MARGIN_DP * density).toInt()
        }
        try {
            getSystemService(WindowManager::class.java).addView(view, params)
            overlay = view
        } catch (_: Exception) {
            overlay = null
        }
    }

    private fun remove() {
        overlay?.let {
            try { getSystemService(WindowManager::class.java).removeView(it) } catch (_: Exception) {}
        }
        overlay = null
    }

    /** From the lock screen this asks to unlock first, then opens on what is playing. */
    private fun openApp() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        } catch (_: Exception) {
        }
    }

    /** Katapult draws the same widget for every player; with it on, this one would stack. */
    private fun katapultShowsIt(): Boolean = try {
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { it.startsWith("$KATAPULT/") }
    } catch (_: Exception) {
        false
    }

    /**
     * The PIN pad is up when a visible text field holds input focus. Looked at, not waited
     * for: showing the pad again does not always fire a focus event.
     */
    private fun pinShowing(): Boolean = try {
        val focus = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val showing = focus != null && focus.isVisibleToUser &&
            focus.className?.contains("EditText") == true
        @Suppress("DEPRECATION")
        focus?.recycle()
        showing
    } catch (_: Exception) {
        false
    }

    /** A ringing or live call draws its own screen over the lock screen. */
    private fun inCall(): Boolean = try {
        when (getSystemService(AudioManager::class.java).mode) {
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION -> true
            else -> false
        }
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val KATAPULT = "com.gezimos.katapult"

        // As wide as the strip inkOS keeps at the foot of its home screen - the screen less
        // 32dp a side. Not as low: the Kompakt's lock screen has its padlock and "Swipe Up"
        // in the bottom 85dp, and a strip over them would take the swipe that unlocks the
        // phone. 96dp up puts it just above them, in the empty band under the clock.
        private const val SIDE_MARGIN_DP = 32f
        private const val BOTTOM_MARGIN_DP = 96f
        private const val RECHECK_MS = 120L

        /** Whether the reader has turned this on in Android's Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val mine = ComponentName(context, LockScreenControls::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any {
                it.equals(mine.flattenToString(), ignoreCase = true) ||
                    it.equals(mine.flattenToShortString(), ignoreCase = true)
            }
        }
    }
}

/**
 * The same strip inkOS draws at the foot of its home screen while something plays: one row,
 * a 2dp rule with 8dp corners, the cover, then previous, play or pause, next and stop, spread
 * evenly. Filled in, where inkOS leaves it clear over the wallpaper, so it reads over the lock
 * screen.
 */
@Composable
private fun ControlsPanel(
    title: String,
    artist: String,
    isPlaying: Boolean,
    art: ByteArray?,
    ink: Color,
    surface: Color,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val bitmap = remember(art) {
        art?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
    }
    val label = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" - ")
        .ifEmpty { stringResource(R.string.lockscreen_nothing_named) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface, shape)
            .border(2.dp, ink, shape)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(shape)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(shape),
                )
            } else {
                Box(
                    modifier = Modifier.size(36.dp).border(2.dp, ink, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = StripGlyphs.MusicNote,
                        contentDescription = label,
                        tint = ink,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Control(StripGlyphs.Previous, R.string.lockscreen_previous, ink, 32, onPrevious)
        Control(
            if (isPlaying) StripGlyphs.Pause else StripGlyphs.Play,
            R.string.lockscreen_play_pause, ink, 42, onPlayPause,
        )
        Control(StripGlyphs.Next, R.string.lockscreen_next, ink, 32, onNext)
        Box(modifier = Modifier.padding(end = 8.dp)) {
            Control(StripGlyphs.Stop, R.string.lockscreen_stop, ink, 32, onStop)
        }
    }
}

@Composable
private fun Control(icon: ImageVector, description: Int, ink: Color, sizeDp: Int, onClick: () -> Unit) {
    Icon(
        imageVector = icon,
        contentDescription = stringResource(description),
        tint = ink,
        modifier = Modifier.size(sizeDp.dp).clickable(onClick = onClick),
    )
}

/**
 * The strip's own glyphs, Material Symbols (Apache-2.0) in their filled cut, carried here so the
 * strip is drawn the same in every app that has this file.
 */
private object StripGlyphs {
    private fun symbol(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        )
            .addGroup(name = name, translationY = 960f)
            .addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black),
            )
            .clearGroup()
            .build()

    val Previous = symbol("Previous", "M220-240v-480h80v480h-80Zm520 0L380-480l360-240v480Z")
    val Play = symbol("Play", "M320-200v-560l440 280-440 280Z")
    val Pause = symbol("Pause", "M560-200v-560h160v560H560Zm-320 0v-560h160v560H240Z")
    val Next = symbol("Next", "M660-240v-480h80v480h-80Zm-440 0v-480l360 240-360 240Z")
    val Stop = symbol("Stop", "M240-240v-480h480v480H240Z")
    val MusicNote = symbol("MusicNote", "M400 -440Q423 -440 442.5 -434.5Q462 -429 480 -418V-840H720V-680H560V-280Q560 -214 513 -167Q466 -120 400 -120Q334 -120 287 -167Q240 -214 240 -280Q240 -346 287 -393Q334 -440 400 -440Z")
}
