package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One preference: what it is called, what it is set to, and what it looks like. */
data class SettingRow(
    val key: String,
    val title: String,
    /** What it is currently set to, or a sentence saying what the row does. Null for About. */
    val value: String?,
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
    onRowClick: (SettingRow) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        ScreenTopBar(title = "Preferences", onClose = onClose)
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
            items(rows, key = { it.key }) { row ->
                SettingLine(row = row, onClick = { onRowClick(row) })
            }
        }
    }
}

@Composable
private fun SettingLine(row: SettingRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, bottom = 25.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = row.title,
                fontSize = 19.5.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Black,
            )
            if (row.value != null) {
                Text(
                    text = row.value,
                    fontSize = 17.5.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.Black,
                )
            }
        }
    }
}
