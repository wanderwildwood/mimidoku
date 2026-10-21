package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.mudita.mmd.components.text.TextMMD
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.mimidoku.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One row in the library: a shelf holding books, or a book itself. */
data class LibraryRow(
    val title: String,
    val id: String,
    /**
     * What removing the shelf would take, said on the armed row -- or null where a shelf cannot
     * be removed: a genre, a status, or an author with nothing on the phone.
     */
    val removeNote: String? = null,
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
    onRemoveShelf: ((LibraryRow) -> Unit)? = null,
    // What the shelves are sorted by, in their order; a row after them, the shelf of books that
    // name nothing, is left out.
    sortedNames: List<String> = rows.map { it.title },
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

        ListJumpBox(
            state = listState,
            jump = remember(sortedNames, onRecentClick != null) {
                ListJump.Letters(sortedNames, firstIndex = if (onRecentClick != null) 1 else 0)
            },
            modifier = Modifier.weight(1f),
        ) {
            PagedColumn(state = listState, onPullAtTop = onPullAtTop) {
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
                        onRemove = onRemoveShelf?.takeIf { row.removeNote != null }?.let { { it(row) } },
                    )
                }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfRow(
    row: LibraryRow,
    icon: ImageVector = Icons.Folder,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    // Held, an author's row arms: a press opens the shelf, so removing every book on it takes a
    // hold and then a second press, and the row disarms itself if that press does not come.
    var armed by remember(row.id) { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(ARMED_MS)
            armed = false
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(63.dp)
            .combinedClickable(
                onClick = {
                    if (armed && onRemove != null) {
                        armed = false
                        onRemove()
                    } else {
                        onClick()
                    }
                },
                onLongClick = onRemove?.let { { armed = true } },
            )
            .padding(start = 32.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Armed, the row is a question rather than a shelf, and the folder gives way so the
        // question can be read whole.
        if (!armed) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(34.dp),
            )
            Spacer(modifier = Modifier.width(18.dp))
        }
        if (armed) {
            Column {
                TextMMD(
                    text = stringResource(R.string.remove_armed),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextMMD(
                    text = row.removeNote.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            TextMMD(
                text = row.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.Black,
                maxLines = 1,
            )
        }
    }
}

/** The Recent row's key, kept clear of every shelf name: no author is called this. */
private const val RECENT = "\u0000recent"
