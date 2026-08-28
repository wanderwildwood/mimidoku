package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What the Now Playing bar shows when there is something loaded. */
data class NowPlaying(
    val title: String,
    val remaining: String,
    val isPlaying: Boolean,
)

/**
 * The Now Playing bar.
 *
 * It names what is loaded and how much of it is left, and gives one button. Tapping the rest of it
 * opens the book, which is what someone reaching for this strip almost always wants.
 */
@Composable
fun NowPlayingBar(
    nowPlaying: NowPlaying,
    onClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = nowPlaying.title,
            fontSize = 16.sp,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = nowPlaying.remaining, fontSize = 15.sp, color = Color.Black)
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .clickable(onClick = onPlayPauseClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (nowPlaying.isPlaying) Icons.Pause else Icons.Play,
                contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play",
                tint = Color.White,
                // A triangle looks off-centre in a circle when it is centred, because its weight
                // sits to the left of its bounding box. Nudged right until it stops looking wrong.
                modifier = Modifier.size(19.dp).offset(x = 1.dp),
            )
        }
    }
}

