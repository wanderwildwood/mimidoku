package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

/** One row in the library: a shelf holding books, or a book itself. */
data class LibraryRow(
    val title: String,
    val id: String,
)

/**
 * The library.
 *
 * Text and a folder, at a size that can be read at arm's length on a small panel, and nothing that
 * moves. What is on the shelf is named; how many, how long and what it looks like are not asked
 * for and not shown.
 */
@Composable
fun LibraryScreen(
    rows: List<LibraryRow>,
    status: String?,
    nowPlaying: NowPlaying?,
    onRowClick: (LibraryRow) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNowPlayingClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        ScreenTopBar(
            title = "Library",
            afterTitle = {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Search,
                    contentDescription = "What are you looking for?",
                    tint = Color.Black,
                    modifier = Modifier.size(25.dp).clickable(onClick = onSearchClick),
                )
            },
            trailing = {
                Icon(
                    imageVector = Icons.Settings,
                    contentDescription = "Settings",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp).clickable(onClick = onSettingsClick),
                )
            },
        )

        if (status != null) {
            Text(
                text = status,
                fontSize = 14.sp,
                color = Color.Black,
                modifier = Modifier.padding(start = 16.dp, bottom = 22.dp),
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(rows, key = { it.id }) { row ->
                ShelfRow(row = row, onClick = { onRowClick(row) })
            }
        }

        if (nowPlaying != null) {
            NowPlayingBar(
                nowPlaying = nowPlaying,
                onClick = onNowPlayingClick,
                onPlayPauseClick = onPlayPauseClick,
            )
        }
    }
}

@Composable
private fun ShelfRow(row: LibraryRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(63.dp)
            .clickable(onClick = onClick)
            .padding(start = 32.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Folder,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(34.dp),
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = row.title,
            fontSize = 24.sp,
            color = Color.Black,
            maxLines = 1,
        )
    }
}
