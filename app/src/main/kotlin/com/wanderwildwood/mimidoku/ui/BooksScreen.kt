package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.mimidoku.R
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import com.mudita.mmd.components.text.TextMMD
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD

/** One book on a shelf, as the list needs it. */
data class BookRow(
    val id: String,
    val title: String,
    val author: String?,
    /** Null while the durations are still being read, and then the book's whole length. */
    val duration: String?,
    /** How far in the reader is, once they have started. Null until they have. */
    val percent: String?,
    /**
     * Where the book is, when that is not simply "here". A book on a server has to be fetched
     * before it can be read, and this is the only place a shelf can say so -- it shares the slot
     * with [percent] because a book that is not on the phone cannot have been started, so the two
     * are never both worth showing.
     */
    val state: String? = null,
    /** Whether there is anything of it on the phone to take off. */
    val removable: Boolean = false,
    /** Whether a server has it too, which removing it from the phone leaves alone. */
    val fromServer: Boolean = false,
    /** Other copies of the same book, folded into this row; removing the row removes them too. */
    val copies: List<String> = emptyList(),
)

/**
 * One shelf: the books filed under an author, or under nothing — or the books lately read.
 *
 * Each book is a plain row, the title over one line of what is worth knowing before pressing it,
 * with a rule between books as every other list in these apps has. The author is said only where
 * the shelf does not already say it ([showAuthor]).
 */
@Composable
fun BooksScreen(
    shelf: String,
    books: List<BookRow>,
    nowPlaying: NowPlaying?,
    onClose: () -> Unit,
    onBookClick: (BookRow) -> Unit,
    onNowPlayingClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    showAuthor: Boolean = false,
    onRemove: ((BookRow) -> Unit)? = null,
    // Held by the caller, so the shelf is where it was left when a book is closed again.
    listState: LazyListState = rememberLazyListState(),
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        ScreenTopBar(title = shelf, onClose = onClose)

        LazyColumnMMD(modifier = Modifier.weight(1f), state = listState) {
            items(books, key = { it.id }) { book ->
                BookLine(
                    book = book,
                    showAuthor = showAuthor,
                    onClick = { onBookClick(book) },
                    onRemove = onRemove?.takeIf { book.removable }?.let { { it(book) } },
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

/**
 * One book, as a row. Shared with search and Recent, which name the author because their books
 * come from every shelf.
 *
 * The title is set in the list size the rest of the app's rows use rather than a card's small
 * caps and 16sp, and under it one line: who, if asked, how long, and how far in — or, for a book
 * not on the phone, where it is, since a book that has to be fetched cannot have been started.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookLine(book: BookRow, showAuthor: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    // A press opens the book, so taking it off the phone is a hold: the row arms, says what a
    // second press will do, and disarms itself after four seconds so nothing is left live.
    var armed by remember(book.id) { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(ARMED_MS)
            armed = false
        }
    }
    val details = if (armed) {
        if (book.fromServer) stringResource(R.string.remove_server_stays) else book.title
    } else {
        listOfNotNull(
            book.author?.takeIf { showAuthor },
            book.duration,
            book.state ?: book.percent,
        ).joinToString(" · ")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        TextMMD(
            text = if (armed) stringResource(R.string.remove_armed) else book.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (details.isNotEmpty()) {
            TextMMD(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    // A hairline, as Files has between its rows: MMD's default rule is the weight of a top bar's,
    // and under every book it reads as a stack of headings.
    HorizontalDividerMMD(thickness = 0.5.dp)
}

/** How long an armed row waits for its second press, as every armed row in these apps does. */
const val ARMED_MS = 4_000L
