package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.lazy.LazyColumnMMD

/** One chapter, as the list shows it: which one it is, what it is called, where it starts. */
data class ChapterRow(
    val id: Int,
    val number: Int,
    val name: String,
    /** Where it begins in the whole book, as a clock. */
    val startsAt: String,
)

/**
 * The chapters of what is playing.
 *
 * Reached by tapping the chapter name on the playback screen, which is where a reader looking for
 * a different chapter is already looking. The one playing is drawn inverted rather than ticked:
 * on a panel with no colour, filling the row is the only emphasis that survives at a glance, and
 * the list opens scrolled to it.
 */
@Composable
fun ChapterSheet(
    chapters: List<ChapterRow>,
    playingIndex: Int,
    onPick: (ChapterRow) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(playingIndex, chapters) {
        if (playingIndex >= 0) listState.scrollToItem(playingIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Scrim)
            // Tapping the part of the screen the sheet does not cover puts it away. No ripple and
            // no highlight: this is a way out, not a button.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 84.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color.White),
        ) {
            LazyColumnMMD(
                state = listState,
                contentPadding = PaddingValues(top = 3.dp, bottom = 8.dp),
            ) {
                items(chapters, key = { it.id }) { chapter ->
                    ChapterLine(
                        chapter = chapter,
                        playing = chapter.id == playingIndex,
                        onClick = { onPick(chapter) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterLine(chapter: ChapterRow, playing: Boolean, onClick: () -> Unit) {
    val ink = if (playing) Color.White else Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (playing) Color.Black else Color.White)
            .clickable(onClick = onClick)
            .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "${chapter.number}", fontSize = 20.sp, lineHeight = 24.sp, color = ink)
        Spacer(modifier = Modifier.width(21.dp))
        Text(
            text = chapter.name,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            color = ink,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = chapter.startsAt, fontSize = 15.sp, lineHeight = 22.sp, color = ink)
    }
}

/**
 * Heavier than the one under a dialog. A dialog is a question about the screen behind it, which
 * stays legible on purpose; this covers the screen because you have stopped looking at it.
 */
private val Scrim = Color.Black.copy(alpha = 0.73f)
