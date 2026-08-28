package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The bar at the top of every screen: what you are looking at, and the way out of it.
 *
 * A screen the reader opened gets a cross rather than an arrow, because every one of them is a
 * thing you finish and close, not a step in a path you retrace. The first screen has nothing to
 * close and sits a little deeper, which is the one difference between the two.
 */
@Composable
fun ScreenTopBar(
    title: String,
    onClose: (() -> Unit)? = null,
    afterTitle: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (onClose == null) 74.dp else 62.dp)
            .padding(start = if (onClose == null) 23.dp else 17.dp, end = 31.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onClose != null) {
            Icon(
                imageVector = Icons.Close,
                contentDescription = "Close",
                tint = Color.Black,
                modifier = Modifier.size(23.dp).clickable(onClick = onClose),
            )
            Spacer(modifier = Modifier.width(15.dp))
        }
        // The title and whatever follows it share one flexible slot, so that a long shelf name is
        // cut off rather than pushing the buttons at the right edge off the screen.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 24.sp,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            afterTitle()
        }
        trailing()
    }
}
