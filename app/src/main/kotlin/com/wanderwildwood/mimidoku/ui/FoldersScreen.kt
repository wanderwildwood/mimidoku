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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** One folder the reader has granted, and what the app made of it. */
data class FolderRow(
    val id: String,
    val name: String,
    /** True when the folder turned out to be authors holding books rather than books directly. */
    val byAuthor: Boolean,
)

/**
 * The folders the library is read from.
 *
 * More than one, because a reader's books are rarely all in one place — some on the card, some in
 * the download folder — and asking them to reorganise their storage to suit an app is the wrong
 * way round. Each row says how the app read that folder, which is the first thing worth knowing
 * when a book has not turned up.
 */
@Composable
fun FoldersScreen(
    folders: List<FolderRow>,
    onBack: () -> Unit,
    onScanNow: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (FolderRow) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(62.dp).padding(start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Back,
                    contentDescription = "Back",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp).clickable(onClick = onBack),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Scan now",
                    fontSize = 17.5.sp,
                    lineHeight = 22.sp,
                    color = Color.Black,
                    modifier = Modifier.clickable(onClick = onScanNow),
                )
            }

            Text(
                text = "Audiobook folders",
                fontSize = 24.sp,
                lineHeight = 32.sp,
                color = Color.Black,
                modifier = Modifier.padding(start = 16.dp),
            )

            LazyColumn(modifier = Modifier.weight(1f).padding(top = 36.dp)) {
                items(folders, key = { it.id }) { folder ->
                    FolderLine(folder = folder, onRemove = { onRemove(folder) })
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .clickable(onClick = onAdd)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Plus,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Add", fontSize = 19.5.sp, lineHeight = 24.sp, color = Color.White)
        }
    }
}

@Composable
private fun FolderLine(folder: FolderRow, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 27.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (folder.byAuthor) Icons.Person else Icons.Folder,
            contentDescription = if (folder.byAuthor) "Authors, then books" else "Books",
            tint = Color.Black,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = folder.name,
            fontSize = 21.sp,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Delete,
            contentDescription = "Stop reading this folder",
            tint = Color.Black,
            modifier = Modifier.size(24.dp).clickable(onClick = onRemove),
        )
    }
}
