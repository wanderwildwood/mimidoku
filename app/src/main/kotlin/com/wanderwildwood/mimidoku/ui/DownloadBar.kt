package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD

/**
 * The line along the bottom of every screen while a book downloads.
 *
 * A download is minutes long and happens wherever the reader has wandered off to, so it is said
 * wherever they are rather than only on the row of the book. When it ends, what happened is said
 * here for a few seconds too, which is where the reader last saw it going.
 *
 * [progress] is null for a message rather than a download, and then the line does nothing when
 * pressed.
 */
@Composable
fun DownloadBar(
    text: String,
    progress: String?,
    onClick: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        // A rule rather than a shade: the panel has no greys worth trusting.
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextMMD(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (progress != null) {
                Spacer(modifier = Modifier.width(8.dp))
                TextMMD(text = progress, style = MaterialTheme.typography.bodySmall, color = Color.Black)
            }
        }
    }
}
