package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
)

/**
 * One shelf: the books filed under an author, or under nothing.
 *
 * A book gets a card rather than a line because three things have to be read at once — who wrote
 * it, what it is, and how long it will take — and three lines of equal weight in a flat list stop
 * being three facts about one book.
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
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        ScreenTopBar(title = shelf, onClose = onClose)

        LazyColumnMMD(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 9.dp),
        ) {
            items(books, key = { it.id }) { book ->
                BookCard(book = book, onClick = { onBookClick(book) })
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

/** Shared with search, which shows the same card for whatever a query turned up. */
@Composable
fun BookCard(book: BookRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, CardOutline, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(start = 17.dp, end = 13.dp, top = 13.dp, bottom = 13.dp),
    ) {
        if (book.author != null) {
            Text(
                text = book.author.uppercase(),
                fontSize = 14.sp,
                lineHeight = 16.5.sp,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = book.title,
            fontSize = 16.sp,
            lineHeight = 19.5.sp,
            color = Color.Black,
            overflow = TextOverflow.Ellipsis,
        )
        if (book.duration != null) {
            // How long it is and how far in you are belong on one line: they are the same
            // question asked from either end.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = book.duration, fontSize = 15.sp, lineHeight = 18.sp, color = Color.Black)
                if (book.percent != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = book.percent, fontSize = 15.sp, lineHeight = 18.sp, color = Color.Black)
                }
            }
        }
    }
}

/**
 * The card's edge is nearly white, which on the panel is a suggestion of an edge rather than a
 * line. That is the intent: the card groups three lines, and a black rule around every book would
 * be louder than the books.
 */
private val CardOutline = Color(0xFFEBEBEB)
