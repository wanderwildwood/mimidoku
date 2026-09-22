package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.mimidoku.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD

/**
 * The bar at the top of every screen: what you are looking at, and the way out of it.
 *
 * A screen the reader opened gets a cross rather than an arrow, because every one of them is a
 * thing you finish and close, not a step in a path you retrace.
 *
 * This was a hand-built Row at 74dp or 62dp with a 24sp title and no rule beneath it, while
 * every other app of this shop used [TopAppBarMMD] — 64dp, `titleLarge`, and a 3dp rule under
 * the bar. Two of the three numbers already matched MMD by hand; the bar is MMD's now, so they
 * will go on matching when Mudita changes them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(
    title: String,
    onClose: (() -> Unit)? = null,
    afterTitle: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    TopAppBarMMD(
        title = {
            // The title and whatever follows it share one flexible slot, so that a long shelf
            // name is cut off rather than pushing the buttons at the right edge off the screen.
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextMMD(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                afterTitle()
            }
        },
        navigationIcon = {
            if (onClose != null) BarButton(Icons.Close, stringResource(R.string.cd_close), onClose)
        },
        actions = trailing,
    )
}

/**
 * A press in the bar: a 22dp mark in a 48dp target.
 *
 * The same three numbers as every other app here. What stood in Settings was a bare 24dp icon
 * with `clickable` hung off it, which is a target half the size the rest of the shop gives you.
 */
@Composable
fun BarButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}
