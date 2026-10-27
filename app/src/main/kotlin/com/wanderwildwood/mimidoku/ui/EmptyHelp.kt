package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD

/**
 * What a screen with nothing on it says: why it is empty and what to press next, in words.
 * Each action is a full-width button, as tall as a thumb needs.
 */
@Composable
fun EmptyHelp(message: String, actions: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)) {
        TextMMD(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp,
            color = Color.Black,
        )
        actions.forEach { (label, onClick) ->
            Spacer(Modifier.height(16.dp))
            OutlinedButtonMMD(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                TextMMD(text = label, style = MaterialTheme.typography.titleSmall, color = Color.Black)
            }
        }
    }
}
