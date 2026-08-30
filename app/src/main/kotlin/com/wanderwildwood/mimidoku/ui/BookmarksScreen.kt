package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.mudita.mmd.components.lazy.LazyColumnMMD

/** One marked place, as the list needs it. */
data class BookmarkRow(
    val id: Long,
    /** When it was made, in words: "3 minutes ago, 11:01 PM". */
    val when_: String,
    /** Where it points, as a clock. */
    val position: String,
    val automatic: Boolean,
)

/**
 * The places marked in one book.
 *
 * Newest first, because the reason to open this list is almost always the thing just marked or
 * the place playback stopped, and both of those are the most recent.
 */
@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkRow>,
    onClose: () -> Unit,
    onGoTo: (BookmarkRow) -> Unit,
    onDelete: (BookmarkRow) -> Unit,
    onAdd: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTopBar(title = "Bookmark", onClose = onClose)
            LazyColumnMMD(contentPadding = PaddingValues(top = 15.dp, bottom = 96.dp)) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkLine(
                        bookmark = bookmark,
                        onClick = { onGoTo(bookmark) },
                        onDelete = { onDelete(bookmark) },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Plus,
                contentDescription = "Mark this place",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun BookmarkLine(bookmark: BookmarkRow, onClick: () -> Unit, onDelete: () -> Unit) {
    // The row asks, rather than a dialog: one repaint instead of two, and the question is put
    // in the place the answer belongs. It disarms itself, so a stray tap leaves nothing live
    // for whoever picks the phone up next.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(4000)
            armed = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { if (armed) armed = false else onClick() })
            .padding(start = 16.dp, end = 27.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (armed) "Remove this mark — tap again" else bookmark.when_,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // A mark the app made rather than the reader says so with the clock it was made by.
                if (bookmark.automatic) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Timelapse,
                        contentDescription = "Marked automatically",
                        tint = Color.Black,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Text(text = bookmark.position, fontSize = 17.5.sp, lineHeight = 22.sp, color = Color.Black)
        }
        Icon(
            imageVector = Icons.More,
            contentDescription = if (armed) "Remove this mark — tap again" else "Remove this mark",
            tint = Color.Black,
            modifier = Modifier
                .size(24.dp)
                .clickable { if (armed) onDelete() else armed = true },
        )
    }
}
