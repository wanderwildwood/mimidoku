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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.mudita.mmd.components.text.TextMMD
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.lazy.LazyColumnMMD

/** One folder the reader has granted, and what the app made of it. */
data class FolderRow(
    val id: String,
    val name: String,
    /** True when the folder turned out to be authors holding books rather than books directly. */
    val byAuthor: Boolean,
    /**
     * How this folder was read, in the reader's words rather than the app's.
     *
     * A book that has not turned up is nearly always a folder read as something other than what it
     * is, and until this line was here the only sign of that was which of two icons was drawn.
     */
    val how: String,
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
    onChange: (FolderRow) -> Unit,
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
                TextMMD(
                    text = "Scan now",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    color = Color.Black,
                    modifier = Modifier.clickable(onClick = onScanNow),
                )
            }

            TextMMD(
                text = "Audiobook folders",
                style = MaterialTheme.typography.titleLarge,
                lineHeight = 32.sp,
                color = Color.Black,
                modifier = Modifier.padding(start = 16.dp),
            )

            LazyColumnMMD(modifier = Modifier.weight(1f).padding(top = 36.dp)) {
                items(folders, key = { it.id }) { folder ->
                    FolderLine(
                        folder = folder,
                        onRemove = { onRemove(folder) },
                        onChange = { onChange(folder) },
                    )
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
            TextMMD(text = "Add", style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp, color = Color.White)
        }
    }
}

/**
 * The folder, what was made of it, and the way out of a wrong answer.
 *
 * The line itself takes the press: a folder read the wrong way is the one thing a reader comes to
 * this screen to put right, and it should not need a menu.
 */
@Composable
private fun FolderLine(folder: FolderRow, onRemove: () -> Unit, onChange: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 27.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (folder.byAuthor) Icons.Person else Icons.Folder,
            contentDescription = if (folder.byAuthor) "Authors, then books" else "Books",
            tint = Color.Black,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).clickable(onClick = onChange).padding(vertical = 8.dp)) {
            TextMMD(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextMMD(
                text = folder.how,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Icons.Delete,
            contentDescription = "Stop reading this folder",
            tint = Color.Black,
            modifier = Modifier.size(24.dp).clickable(onClick = onRemove),
        )
    }
}
