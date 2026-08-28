package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Everything the playback screen shows about what is loaded. */
data class Playback(
    val author: String?,
    val title: String,
    val chapter: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    /** What the last button press did, shown for a moment and then gone. */
    val announcement: String?,
    val skipSeconds: Int,
    /** How long the sleep timer has left, or null when it is not running. */
    val sleepRemaining: String?,
    val volumeBoosted: Boolean,
    val skipSilence: Boolean,
    /** With the controls locked, only closing the screen and unlocking still work. */
    val locked: Boolean,
)

/** The buttons along the top, which change how playback behaves rather than what is playing. */
data class PlaybackTools(
    val onClose: () -> Unit,
    val onSleepTimer: () -> Unit,
    val onVolume: () -> Unit,
    val onSpeed: () -> Unit,
    val onSkipSilence: () -> Unit,
    val onBookmarks: () -> Unit,
    val onLock: () -> Unit,
)

/** The chapters of what is playing, and the two things that can be done with the list. */
data class Chapters(
    val rows: List<ChapterRow>,
    val playingIndex: Int,
    val open: Boolean,
    val onOpen: () -> Unit,
    val onPick: (ChapterRow) -> Unit,
    val onDismiss: () -> Unit,
)

/** The buttons along the bottom, which move through what is playing. */
data class Transport(
    val onPreviousChapter: () -> Unit,
    val onRewind: () -> Unit,
    val onPlayPause: () -> Unit,
    val onForward: () -> Unit,
    val onNextChapter: () -> Unit,
    val onSeekTo: (Long) -> Unit,
)

/**
 * Playback.
 *
 * Everything that changes how a book sounds is a button along the top, and everything that moves
 * through it is a button along the bottom; between them is what is playing, set large enough to
 * read from across a room. Nothing here is a menu: a reader with the device in one hand and a
 * kitchen in the other should be able to hit any of it without looking twice.
 */
@Composable
fun PlayerScreen(
    playback: Playback,
    tools: PlaybackTools,
    transport: Transport,
    chapters: Chapters,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        val locked = playback.locked
        Row(
            modifier = Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Tool(Icons.Close, "Close", onClick = tools.onClose)
            Tool(
                icon = if (playback.sleepRemaining != null) Icons.SleepTimerOn else Icons.SleepTimerOff,
                description = "Sleep timer",
                label = playback.sleepRemaining,
                enabled = !locked,
                onClick = tools.onSleepTimer,
            )
            Tool(
                icon = if (playback.volumeBoosted) Icons.VolumeBoosted else Icons.Volume,
                description = "Volume boost",
                enabled = !locked,
                onClick = tools.onVolume,
            )
            Tool(Icons.Speed, "Playback speed", enabled = !locked, onClick = tools.onSpeed)
            Tool(
                icon = if (playback.skipSilence) Icons.Compress else Icons.Expand,
                description = "Skip silence",
                enabled = !locked,
                onClick = tools.onSkipSilence,
            )
            Tool(Icons.Bookmarks, "Bookmarks", enabled = !locked, onClick = tools.onBookmarks)
            Tool(
                icon = if (locked) Icons.Locked else Icons.Unlocked,
                description = if (locked) "Unlock the controls" else "Lock the controls",
                onClick = tools.onLock,
            )
        }

        // The line keeps its space whether or not it has anything to say, so that a press which
        // announces itself does not shove the rest of the screen down and back a moment later.
        Box(
            modifier = Modifier.fillMaxWidth().height(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (playback.announcement != null) {
                Text(
                    text = playback.announcement,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color.Black,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            val ink = if (locked) Dimmed else Color.Black
            if (playback.author != null) {
                // Author and chapter are one size - the old player set both from a single
                // style - with the author bold against the italic title between them, so the
                // block reads as a name over a work rather than three lines of one weight.
                // Measured against that player on the phone: 24sp lands on its ascender, 23
                // falls a pixel short.
                Text(
                    text = playback.author,
                    fontSize = 24.sp,
                    lineHeight = 29.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = playback.title,
                fontSize = 27.5.sp,
                lineHeight = 34.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
                color = ink,
            )
            // A book with one part has no chapter worth naming: the line would repeat the title
            // back in smaller type.
            if (playback.chapter.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                // The name of what is playing is also the way to the rest of the book: a reader
                // hunting for another chapter is already looking straight at it. The whole line
                // takes the press, not just the letters.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !locked, onClick = chapters.onOpen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = playback.chapter,
                        fontSize = 24.sp,
                        lineHeight = 29.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = ink,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Without this the line is a third line of text and the list behind it is
                    // invisible. It is the only mark on this screen saying there is more here.
                    Icon(
                        imageVector = Icons.ExpandMore,
                        contentDescription = "Chapters",
                        tint = ink,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(28.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(44.dp))

        SeekBar(
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            enabled = !locked,
            onSeekTo = transport.onSeekTo,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Both clocks are written to the same width, so the digits do not shuffle sideways
            // every time the hour rolls over on a book long enough to have two of them.
            val hours = playback.durationMs.hourDigits()
            Clock(playback.positionMs, hours, locked)
            Clock(playback.durationMs, hours, locked)
        }

        Spacer(modifier = Modifier.height(25.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 35.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TransportButton(Icons.PreviousChapter, "Previous chapter", 36.dp, locked, transport.onPreviousChapter)
            TransportButton(Icons.Rewind, "Back ${playback.skipSeconds} seconds", 32.dp, locked, transport.onRewind) {
                "${playback.skipSeconds}s"
            }
            TransportButton(
                icon = if (playback.isPlaying) Icons.Pause else Icons.Play,
                description = if (playback.isPlaying) "Pause" else "Play",
                size = 48.dp,
                locked = locked,
                onClick = transport.onPlayPause,
            )
            TransportButton(Icons.Forward, "On ${playback.skipSeconds} seconds", 32.dp, locked, transport.onForward) {
                "${playback.skipSeconds}s"
            }
            TransportButton(Icons.NextChapter, "Next chapter", 36.dp, locked, transport.onNextChapter)
        }

        Spacer(modifier = Modifier.height(42.dp))
    }

    if (chapters.open) {
        ChapterSheet(
            chapters = chapters.rows,
            playingIndex = chapters.playingIndex,
            onPick = chapters.onPick,
            onDismiss = chapters.onDismiss,
        )
    }
  }
}

/**
 * One button along the top, and under it whatever it has to say about itself.
 *
 * Only the sleep timer says anything, and only while it is running, which is exactly when the
 * reader wants to know how long is left without having to open it.
 */
@Composable
private fun Tool(
    icon: ImageVector,
    description: String,
    label: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // Fixed to the icon's width, with the label allowed to spill past it: a countdown is wider
    // than a moon, and without this the whole row would shuffle sideways the moment a timer
    // started.
    Column(
        modifier = Modifier.width(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) Color.Black else DimmedTool,
            modifier = Modifier.size(24.dp).clickable(enabled = enabled, onClick = onClick),
        )
        if (label != null) {
            Text(
                text = label,
                fontSize = 14.sp,
                lineHeight = 14.sp,
                color = Color.Black,
                softWrap = false,
                modifier = Modifier.wrapContentWidth(unbounded = true),
            )
        }
    }
}

/**
 * A transport button, and under two of them the number of seconds they move.
 *
 * The label is drawn at the bottom of the row rather than under the icon, so that adding it does
 * not push the arrows up out of line with the play button beside them.
 */
@Composable
private fun TransportButton(
    icon: ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    locked: Boolean,
    onClick: () -> Unit,
    label: (() -> String)? = null,
) {
    Box(
        modifier = Modifier.fillMaxHeight().clickable(enabled = !locked, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (locked) Dimmed else Color.Black,
            modifier = Modifier.size(size),
        )
        if (label != null) {
            Text(
                text = label(),
                fontSize = 14.sp,
                lineHeight = 14.sp,
                color = if (locked) Dimmed else Color.Black,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun Clock(ms: Long, hourDigits: Int, locked: Boolean) {
    Text(
        text = ms.asClock(hourDigits),
        fontSize = 20.sp,
        lineHeight = 24.sp,
        color = if (locked) Dimmed else Color.Black,
    )
}

/**
 * Where the reader is in the chapter, and a way to move it.
 *
 * Drawn rather than taken from the toolkit: the stock slider animates its thumb, grows it on
 * press and draws a halo around it, all of which are redraws the panel pays for and none of which
 * say anything a filled line does not.
 */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, enabled: Boolean, onSeekTo: (Long) -> Unit) {
    var width by remember { mutableIntStateOf(0) }
    val knob = with(LocalDensity.current) { KnobSize.toPx() }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val travel = (width - knob).coerceAtLeast(0f)

    fun seek(x: Float) {
        if (enabled && durationMs > 0 && travel > 0f) {
            onSeekTo((durationMs * ((x - knob / 2) / travel).coerceIn(0f, 1f)).toLong())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(KnobSize)
            .onSizeChanged { width = it.width }
            .pointerInput(durationMs, width) {
                detectHorizontalDragGestures { change, _ -> seek(change.position.x) }
            }
            .pointerInput(durationMs, width) {
                detectTapGestures { seek(it.x) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // The track stops where the knob's travel stops, half a knob in from each end, so that a
        // book at the very beginning or the very end has the knob sitting on the track rather than
        // hanging off it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KnobSize / 2)
                .height(2.dp)
                .background(TrackGrey),
        )
        Box(
            modifier = Modifier
                .offset { IntOffset((travel * fraction).toInt(), 0) }
                .size(KnobSize)
                .clip(CircleShape)
                .background(if (enabled) Color.Black else Dimmed),
        )
    }
}

private val KnobSize = 16.dp

private val TrackGrey = Color(0xFFB2B2B2)

/**
 * What a locked control is drawn in.
 *
 * Grey rather than hidden: the reader locked the screen on purpose and should still be able to see
 * what is playing and how far in they are. Only the pressing is taken away.
 */
private val Dimmed = Color(0xFF666666)

/**
 * The toolbar dims further than the body does.
 *
 * What is along the top cannot be pressed at all while the screen is locked, so it recedes; what
 * is in the middle is still worth reading, and stays legible enough to read.
 */
private val DimmedTool = Color(0xFF9F9F9F)

private fun Long.hourDigits(): Int = ((this / 1000) / 3600).toString().length

private fun Long.asClock(hourDigits: Int): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "${hours.toString().padStart(hourDigits, '0')}:" +
        "${minutes.toString().padStart(2, '0')}:" +
        seconds.toString().padStart(2, '0')
}
