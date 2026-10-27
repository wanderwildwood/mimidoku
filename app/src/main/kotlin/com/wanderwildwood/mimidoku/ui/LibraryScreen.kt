package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.mudita.mmd.components.text.TextMMD
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.mimidoku.R
import androidx.compose.ui.unit.dp

/** One row in the library: a shelf holding books, or a book itself. */
data class LibraryRow(
    val title: String,
    val id: String,
    /** Whether some of the shelf is on the phone to take off: an author with books on it, not a genre or a status. */
    val removable: Boolean = false,
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
    onRecentClick: (() -> Unit)? = null,
    // With no books at all: where they come from. Null leaves that button out.
    onChooseFolder: (() -> Unit)? = null,
    onConnectServer: (() -> Unit)? = null,
    onRemoveShelf: ((LibraryRow) -> Unit)? = null,
    // Held by the caller, so the list is where it was left when a shelf is closed again.
    listState: LazyListState = rememberLazyListState(),
    // Only with a server: a swipe down on the first page reads its catalogue again. A library
    // that lives on the card alone has nothing to ask, and the swipe does nothing there.
    onPullAtTop: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        ScreenTopBar(
            title = stringResource(R.string.library_title),
            // Search sits with Settings at the far end, the way the Kompakt's own apps keep
            // their actions, rather than hanging off the title.
            trailing = {
                BarButton(Icons.Search, stringResource(R.string.library_cd_search), onSearchClick)
                BarButton(Icons.Settings, stringResource(R.string.library_cd_settings), onSettingsClick)
            },
        )

        if (status != null) {
            TextMMD(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Black,
                modifier = Modifier.padding(start = 16.dp, bottom = 22.dp),
            )
        }

        if (rows.isEmpty() && status == null) {
            EmptyHelp(
                message = stringResource(R.string.library_empty),
                actions = listOfNotNull(
                    onChooseFolder?.let { stringResource(R.string.library_empty_folder) to it },
                    onConnectServer?.let { stringResource(R.string.library_empty_server) to it },
                ),
                modifier = Modifier.weight(1f),
            )
        } else PagedColumn(state = listState, onPullAtTop = onPullAtTop, modifier = Modifier.weight(1f).fillMaxWidth()) {
            // The books lately read, above the shelves, once there are any: the way back to a
            // book is usually the book that was being read, and that should not mean
            // remembering whose shelf it sits on.
            if (onRecentClick != null) {
                item(key = RECENT) {
                    ShelfRow(
                        row = LibraryRow(title = stringResource(R.string.library_recent), id = RECENT),
                        icon = Icons.Recent,
                        onClick = onRecentClick,
                    )
                }
            }
            items(rows, key = { it.id }) { row ->
                ShelfRow(
                    row = row,
                    onClick = { onRowClick(row) },
                    onRemove = onRemoveShelf?.takeIf { row.removable }?.let { { it(row) } },
                )
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
private fun ShelfRow(
    row: LibraryRow,
    icon: ImageVector = Icons.Folder,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(63.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onClick)
                .padding(start = 32.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(34.dp),
            )
            Spacer(modifier = Modifier.width(18.dp))
            TextMMD(
                text = row.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.Black,
                maxLines = 1,
            )
        }
        // An author's shelf with books on the phone can be taken off it, by name, in words.
        if (onRemove != null) {
            RemoveButton(onRemove)
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** The Recent row's key, kept clear of every shelf name: no author is called this. */
private const val RECENT = "\u0000recent"
