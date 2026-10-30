package com.wanderwildwood.mimidoku.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.mimidoku.R
import kotlinx.coroutines.delay

/**
 * What taking a book (or every book of an author) off the phone means, said in full.
 *
 * Taking a book off the shelf and deleting the reader's audio files are two different things
 * and are two different buttons. The delete is an armed one: it changes to say what a second
 * press will do and gives up after [ARMED_MS], so a stray press leaves nothing live.
 *
 * @param onCard whether any of the books is a folder of the reader's own files
 * @param downloaded whether any is a copy this app fetched from the server
 */
@Composable
fun RemoveDialog(
    title: String,
    files: Int,
    bytes: Long,
    onCard: Boolean,
    downloaded: Boolean,
    onShelf: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(ARMED_MS)
            armed = false
        }
    }
    val context = LocalContext.current
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = title, style = MaterialTheme.typography.titleLarge, lineHeight = 32.sp, color = Color.Black)
        if (files > 0) {
            Spacer(Modifier.height(8.dp))
            TextMMD(
                text = pluralStringResource(
                    R.plurals.remove_summary, files, files, Formatter.formatShortFileSize(context, bytes),
                ),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                color = Color.Black,
            )
        }
        Spacer(Modifier.height(16.dp))
        // The reader's own files can stay: this is the safe choice and it comes first.
        Choice(stringResource(R.string.remove_shelf_action), stringResource(R.string.remove_shelf_note)) {
            onShelf(); onDismiss()
        }
        Spacer(Modifier.height(12.dp))
        val delete = when {
            onCard -> Triple(R.string.remove_delete_action, R.string.remove_delete_armed, R.string.remove_delete_note)
            downloaded -> Triple(R.string.remove_download_action, R.string.remove_download_armed, R.string.remove_download_note)
            else -> null
        }
        if (delete != null) {
            Choice(
                label = stringResource(if (armed) delete.second else delete.first),
                note = stringResource(delete.third),
                // Armed, the choice is drawn the other way round, as the house armed rows are.
                solid = armed,
            ) {
                if (armed) { armed = false; onDelete(); onDismiss() } else armed = true
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            Box(
                modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onDismiss).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                TextMMD(
                    text = stringResource(R.string.dialog_cancel),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    color = Color.Black,
                )
            }
        }
    }
}

@Composable
private fun Choice(label: String, note: String, solid: Boolean = false, onClick: () -> Unit) {
    HouseButton(label, onClick, Modifier.fillMaxWidth(), on = solid, note = note, maxLines = 2)
}
