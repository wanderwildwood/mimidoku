package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        ScreenTopBar(title = shelf, onClose = onClose)

        LazyColumnMMD(modifier = Modifier.weight(1f)) {
            items(books, key = { it.id }) { book ->
                BookLine(book = book, showAuthor = showAuthor, onClick = { onBookClick(book) })
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
@Composable
fun BookLine(book: BookRow, showAuthor: Boolean, onClick: () -> Unit) {
    val details = listOfNotNull(
        book.author?.takeIf { showAuthor },
        book.duration,
        book.state ?: book.percent,
    ).joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
    // A hairline, as Files has between its rows: MMD's default rule is the weight of a top bar's,
    // and under every book it reads as a stack of headings.
    HorizontalDividerMMD(thickness = 0.5.dp)
}
