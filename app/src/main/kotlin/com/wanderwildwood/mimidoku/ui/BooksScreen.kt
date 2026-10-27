package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.mimidoku.R
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

        if (books.isEmpty()) {
            EmptyHelp(
                message = stringResource(R.string.shelf_empty),
                actions = listOf(stringResource(R.string.shelf_empty_back) to onClose),
                modifier = Modifier.weight(1f),
            )
        } else PagedColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
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
 *
 * A book with something on the phone to take off has a plain "Remove" at the end of its row: it
 * opens a question that says exactly what each answer does.
 */
@Composable
fun BookLine(book: BookRow, showAuthor: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    val details = listOfNotNull(
        book.author?.takeIf { showAuthor },
        book.duration,
        book.state ?: book.percent,
    ).joinToString(" · ")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (onRemove == null) 16.dp else 4.dp),
        ) {
            TextMMD(
                text = book.title,
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
        if (onRemove != null) RemoveButton(onRemove)
    }
    // A hairline, as Files has between its rows: MMD's default rule is the weight of a top bar's,
    // and under every book it reads as a stack of headings.
    HorizontalDividerMMD(thickness = 0.5.dp)
}

/** The word at the end of a row that has something to take off the phone; 48dp to press. */
@Composable
fun RemoveButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(
            text = stringResource(R.string.remove_button),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black,
        )
    }
}

/** How long an armed row waits for its second press, six seconds, because the panel needs a moment just to show the question. */
const val ARMED_MS = 6_000L
