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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.lazy.LazyColumnMMD

/**
 * Search.
 *
 * Everything is listed before anything is typed, because on a library of a few hundred books
 * scrolling is often faster than spelling, and a screen that starts empty makes the reader prove
 * they know what they are looking for before it will help. For the same reason the field is not
 * focused on arrival: raising the keyboard costs a full-screen redraw and covers the list that
 * the reader may well have come here to scroll.
 */
@Composable
fun SearchScreen(
    query: String,
    shelves: List<LibraryRow>,
    found: List<BookRow>,
    nowPlaying: NowPlaying?,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onShelfClick: (LibraryRow) -> Unit,
    onBookClick: (BookRow) -> Unit,
    onNowPlayingClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(73.dp).padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Back,
                contentDescription = "Back",
                tint = Color.Black,
                modifier = Modifier.size(24.dp).clickable(onClick = onBack),
            )
            Spacer(modifier = Modifier.width(16.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Lato, fontWeight = Reading, fontSize = 21.sp, color = Color.Black),
                cursorBrush = SolidColor(Color.Black),
                modifier = Modifier.weight(1f),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))

        // Nothing typed yet: the shelves, so that the list is worth scrolling rather than worth
        // ignoring. Once there is a query it is books that were asked for, and a book takes the
        // same card here as it does on its shelf.
        if (query.isBlank()) {
            LazyColumnMMD(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 72.dp),
            ) {
                items(shelves, key = { it.id }) { shelf ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable { onShelfClick(shelf) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = shelf.title,
                            fontSize = 21.sp,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            LazyColumnMMD(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 7.dp, end = 7.dp, top = 15.dp),
            ) {
                items(found, key = { it.id }) { book ->
                    BookCard(book = book, onClick = { onBookClick(book) })
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
