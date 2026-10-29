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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.mudita.mmd.components.text.TextMMD
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.mimidoku.R
import com.wanderwildwood.mimidoku.library.Progress
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
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
    /** Whether the sleep timer is on at all, and how long is left on it when it is. */
    val sleepArmed: Boolean,
    /** The length the timer is set to, for the button's spoken description. */
    val sleepMinutes: Int = 0,
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
    /** The author's shelf; null for a book whose files never named one. */
    val onAuthor: (() -> Unit)? = null,
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
  var moreOpen by rememberSaveable { mutableStateOf(false) }
  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        val locked = playback.locked
        // Six buttons, each a 48 dp target with its word under it. The two switches that are
        // changed rarely (volume boost, skip silence) sit behind More, where they can say whether
        // they are on; seven bare icons had no room to be 48 dp wide and no words.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Tool(Icons.Close, stringResource(R.string.cd_close), stringResource(R.string.player_tool_close), onClick = tools.onClose)
            Tool(
                icon = if (playback.sleepArmed) Icons.SleepTimerOn else Icons.SleepTimerOff,
                description = if (playback.sleepArmed && playback.sleepMinutes > 0) {
                    pluralStringResource(R.plurals.player_cd_sleep_timer_set, playback.sleepMinutes, playback.sleepMinutes)
                } else {
                    stringResource(R.string.player_cd_sleep_timer)
                },
                label = playback.sleepRemaining ?: stringResource(R.string.player_tool_sleep),
                enabled = !locked,
                onClick = tools.onSleepTimer,
            )
            Tool(Icons.Speed, stringResource(R.string.player_cd_speed), stringResource(R.string.player_tool_speed), enabled = !locked, onClick = tools.onSpeed)
            Tool(Icons.Bookmarks, stringResource(R.string.player_cd_bookmarks), stringResource(R.string.player_tool_bookmarks), enabled = !locked, onClick = tools.onBookmarks)
            Tool(
                icon = if (locked) Icons.Locked else Icons.Unlocked,
                description = stringResource(if (locked) R.string.player_cd_unlock else R.string.player_cd_lock),
                label = stringResource(if (locked) R.string.player_tool_unlock else R.string.player_tool_lock),
                onClick = tools.onLock,
            )
            Tool(Icons.More, stringResource(R.string.player_cd_more), stringResource(R.string.player_tool_more), enabled = !locked) { moreOpen = true }
        }

        // The line keeps its space whether or not it has anything to say, so that a press which
        // announces itself does not shove the rest of the screen down and back a moment later.
        Box(
            modifier = Modifier.fillMaxWidth().height(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (playback.announcement != null) {
                TextMMD(
                    text = playback.announcement,
                    style = MaterialTheme.typography.labelSmall,
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
                // The author leads to the rest of their books; the title leads nowhere,
                // because the book it names is the one already open.
                TextMMD(
                    text = playback.author,
                    modifier = tools.onAuthor?.let { Modifier.heightIn(min = 48.dp).clickable(enabled = !locked, onClick = it) } ?: Modifier,
                    style = MaterialTheme.typography.titleLarge,
                    lineHeight = 29.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextMMD(
                text = playback.title,
                style = MaterialTheme.typography.headlineLarge,
                lineHeight = 34.sp,
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
                        .heightIn(min = 48.dp)
                        .clickable(enabled = !locked, onClick = chapters.onOpen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextMMD(
                        text = playback.chapter,
                        style = MaterialTheme.typography.titleLarge,
                        lineHeight = 29.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = ink,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Without this the line is a third line of text and the list behind it is
                    // invisible. It is the only mark on this screen saying there is more here.
                    Icon(
                        imageVector = Icons.ExpandMore,
                        contentDescription = stringResource(R.string.player_cd_chapters),
                        tint = ink,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(28.dp),
                    )
                }
            }
        }

        // The bar is drawn where it always was: what stands above and below it gives up exactly
        // what the bar took for somewhere to be pressed.
        Spacer(modifier = Modifier.height(44.dp - (SeekBarHeight - KnobSize) / 2))

        SeekBar(
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            enabled = !locked,
            onSeekTo = transport.onSeekTo,
        )

        Spacer(modifier = Modifier.height(20.dp - (SeekBarHeight - KnobSize) / 2))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Both clocks are written to the same width, so the digits do not shuffle sideways
            // every time the hour rolls over on a book long enough to have two of them.
            val hours = playback.durationMs.hourDigits()
            Clock(playback.positionMs, hours, locked)
            Clock(playback.durationMs, hours, locked, unknown = playback.durationMs <= 0)
        }

        Spacer(modifier = Modifier.height(25.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 35.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val skipLabel = stringResource(R.string.player_skip_label, playback.skipSeconds)
            TransportButton(Icons.PreviousChapter, stringResource(R.string.player_cd_previous_chapter), 36.dp, locked, transport.onPreviousChapter)
            TransportButton(Icons.Rewind, pluralStringResource(R.plurals.player_cd_back_seconds, playback.skipSeconds, playback.skipSeconds), 32.dp, locked, transport.onRewind) {
                skipLabel
            }
            TransportButton(
                icon = if (playback.isPlaying) Icons.Pause else Icons.Play,
                description = stringResource(if (playback.isPlaying) R.string.cd_pause else R.string.cd_play),
                size = 48.dp,
                locked = locked,
                onClick = transport.onPlayPause,
            )
            TransportButton(Icons.Forward, pluralStringResource(R.plurals.player_cd_on_seconds, playback.skipSeconds, playback.skipSeconds), 32.dp, locked, transport.onForward) {
                skipLabel
            }
            TransportButton(Icons.NextChapter, stringResource(R.string.player_cd_next_chapter), 36.dp, locked, transport.onNextChapter)
        }

        Spacer(modifier = Modifier.height(42.dp))
    }

    if (moreOpen) {
        MoreDialog(
            volumeBoosted = playback.volumeBoosted,
            skipSilence = playback.skipSilence,
            onVolume = { moreOpen = false; tools.onVolume() },
            onSkipSilence = { moreOpen = false; tools.onSkipSilence() },
            onDismiss = { moreOpen = false },
        )
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

/** The two switches that are set once and left: each row says what it is and whether it is on. */
@Composable
private fun MoreDialog(
    volumeBoosted: Boolean,
    skipSilence: Boolean,
    onVolume: () -> Unit,
    onSkipSilence: () -> Unit,
    onDismiss: () -> Unit,
) {
    EInkDialog(onDismiss = onDismiss) {
        DialogTitle(stringResource(R.string.player_more_title))
        Spacer(modifier = Modifier.height(8.dp))
        MoreRow(stringResource(R.string.player_cd_volume_boost), volumeBoosted, onVolume)
        MoreRow(stringResource(R.string.player_cd_skip_silence), skipSilence, onSkipSilence)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DialogAction(stringResource(R.string.dialog_cancel), onDismiss)
        }
    }
}

@Composable
private fun MoreRow(name: String, on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(text = name, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp, color = Color.Black, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(16.dp))
        TextMMD(
            text = stringResource(if (on) R.string.player_state_on else R.string.player_state_off),
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp,
            color = Color.Black,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * One button along the top: a 48 dp target, and under it one word. While a sleep timer runs the
 * word is the time left, which is exactly when the reader wants to know it.
 */
@Composable
private fun RowScope.Tool(
    icon: ImageVector,
    description: String,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.weight(1f).clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = Color.Black,
                modifier = Modifier.size(24.dp),
            )
        }
        TextMMD(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            lineHeight = 16.sp,
            color = Color.Black,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(bottom = 4.dp),
        )
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
        modifier = Modifier.fillMaxHeight().widthIn(min = 48.dp).clickable(enabled = !locked, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (locked) Dimmed else Color.Black,
            modifier = Modifier.size(size),
        )
        if (label != null) {
            TextMMD(
                text = label(),
                style = MaterialTheme.typography.labelSmall,
                lineHeight = 14.sp,
                color = if (locked) Dimmed else Color.Black,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun Clock(ms: Long, hourDigits: Int, locked: Boolean, unknown: Boolean = false) {
    TextMMD(
        // A length nobody has read yet is not zero, and 0:00:00 would say the chapter is empty.
        text = if (unknown) stringResource(R.string.player_length_unknown) else ms.asClock(hourDigits),
        style = MaterialTheme.typography.bodyLarge,
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
    val fraction = Progress.seekFraction(positionMs, durationMs)
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
            // Sixteen dense pixels of line is something to look at, not something to catch: a
            // drag has to start inside it, and a thumb on a bus does not land that accurately.
            // The strip that takes the press is as tall as a button; the line stays a line.
            .height(SeekBarHeight)
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

/** How much of the screen listens for the bar. Twice a fingertip, and all of it pressable. */
private val SeekBarHeight = 44.dp

private val TrackGrey = Color.Black

/**
 * What a locked control is drawn in.
 *
 * Black like everything else, not grey (a grey is a dither on E Ink): the reader locked the screen
 * on purpose and should still be able to see what is playing and how far in they are. Only the
 * pressing is taken away, and the closed padlock says so.
 */
private val Dimmed = Color.Black

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
