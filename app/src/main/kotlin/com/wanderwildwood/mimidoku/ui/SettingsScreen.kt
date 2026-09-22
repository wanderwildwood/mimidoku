package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.mimidoku.R
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.lazy.LazyColumnMMD

/** One preference: what it is called, what it is set to, and what it looks like. */
data class SettingRow(
    val key: String,
    val title: String,
    /** What it is currently set to, or a sentence saying what the row does. Null for About. */
    val value: String?,
    /**
     * On or off, for a preference that is only ever one of the two. Such a row draws a switch and
     * needs no [value]: the switch is already showing what it is set to, and a second line saying
     * "On" underneath it would be the same fact twice.
     */
    val toggle: Boolean? = null,
    /**
     * A second setting to share this row, as two columns. For a pair that is only ever read
     * together - a start and an end - two full rows state one fact twice and put a stack of
     * other settings between the halves of it.
     */
    val beside: SettingRow? = null,
    /**
     * Drawn indented, under the row above it, which is the setting that governs it. A setting
     * that only exists while a switch is on belongs to that switch, and saying so by where it
     * sits costs nothing and needs no words.
     */
    val beneath: Boolean = false,
)

/**
 * Preferences.
 *
 * Every row says what it is set to without being opened, because a settings screen that only
 * names its settings makes the reader open all of them to find the one that is wrong.
 */
@Composable
fun SettingsScreen(
    rows: List<SettingRow>,
    onClose: () -> Unit,
    onAbout: () -> Unit,
    onRowClick: (SettingRow) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        ScreenTopBar(
            title = stringResource(R.string.settings_title),
            onClose = onClose,
            trailing = { BarButton(Icons.Info, stringResource(R.string.settings_cd_about), onAbout) },
        )
        LazyColumnMMD(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
            items(rows, key = { it.key }) { row ->
                SettingLine(row = row, onClick = onRowClick)
            }
        }
    }
}

@Composable
private fun SettingLine(row: SettingRow, onClick: (SettingRow) -> Unit) {
    if (row.beside != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = indent(row), end = 16.dp, bottom = 25.5.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Each half takes its own press: they are two settings, drawn as one line because
            // that is how they are read.
            Label(row, Modifier.weight(1f).clickable { onClick(row) })
            Label(row.beside, Modifier.weight(1f).clickable { onClick(row.beside) })
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(row) }
            .padding(start = indent(row), end = 16.dp, bottom = 25.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(row, Modifier.weight(1f))
        if (row.toggle != null) {
            Spacer(modifier = Modifier.width(16.dp))
            SwitchMMD(checked = row.toggle, onCheckedChange = null)
        }
    }
}

/** Where a row starts: indented if it belongs to the setting above it. */
private fun indent(row: SettingRow) = if (row.beneath) 32.dp else 16.dp

/** What a setting is called, over what it is set to. */
@Composable
private fun Label(row: SettingRow, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        TextMMD(
            text = row.title,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
            color = Color.Black,
        )
        if (row.value != null) {
            TextMMD(
                text = row.value,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Black,
            )
        }
    }
}

