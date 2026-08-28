package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * The shell every dialog here is built in: a white card over a dimmed screen.
 *
 * Deliberately not Material's AlertDialog, which sizes itself to its buttons and animates in. On
 * the panel an animated entrance is a smear, and a dialog that changes width with its content
 * reads as a different dialog each time.
 */
@Composable
private fun DialogCard(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(start = 25.dp, end = 25.dp, top = 24.dp, bottom = 36.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(text = text, fontSize = 24.sp, lineHeight = 32.sp, color = Color.Black)
}

/**
 * A number the reader nudges rather than types.
 *
 * There is no keyboard on this device worth opening for a two-digit number, and every value that
 * makes sense for these settings is a few presses from where it already is.
 */
@Composable
fun StepperDialog(
    title: String,
    initial: Int,
    range: IntProgression,
    label: (Int) -> String,
    onDismiss: () -> Unit,
    onSet: (Int) -> Unit,
    /**
     * A live stepper applies every press at once and has no buttons to confirm with. That suits a
     * setting the reader can hear the effect of — playback speed — and not one they cannot.
     */
    live: Boolean = false,
) {
    var value by remember { mutableIntStateOf(initial) }
    fun change(to: Int) {
        value = to
        if (live) onSet(to)
    }
    DialogCard(onDismiss = onDismiss) {
        DialogTitle(title)
        Spacer(modifier = Modifier.height(21.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = Icons.Minus,
                contentDescription = "Less",
                tint = Color.Black,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { change((value - range.step).coerceAtLeast(range.first)) },
            )
            Text(text = label(value), fontSize = 29.sp, lineHeight = 36.sp, color = Color.Black)
            Icon(
                imageVector = Icons.Plus,
                contentDescription = "More",
                tint = Color.Black,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { change((value + range.step).coerceAtMost(range.last)) },
            )
        }
        if (live) {
            // A dialog with no buttons still needs a floor under the number, or the card reads as
            // having been cut off.
            Spacer(modifier = Modifier.height(20.dp))
        } else {
            Spacer(modifier = Modifier.height(44.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                DialogAction("Cancel", onDismiss)
                Spacer(modifier = Modifier.width(31.dp))
                DialogAction("Set") { onSet(value); onDismiss() }
            }
        }
    }
}

/**
 * One question with one answer worth giving.
 *
 * Used where a press would otherwise throw something away silently. The three dots on a bookmark
 * promise a choice, and this is the choice.
 */
@Composable
fun ConfirmDialog(
    title: String,
    action: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DialogCard(onDismiss = onDismiss) {
        DialogTitle(title)
        Spacer(modifier = Modifier.height(44.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            DialogAction("Cancel", onDismiss)
            Spacer(modifier = Modifier.width(31.dp))
            DialogAction(action) { onConfirm(); onDismiss() }
        }
    }
}

/** One fact about the app, with the thing it is about beside it. */
data class AboutLine(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val value: String,
)

/**
 * What this is and what it is built out of.
 *
 * The borrowed parts are named here rather than buried in a file nobody opens: a typeface and an
 * icon set were made by other people under licences that ask to be credited, and crediting them
 * is a line of text.
 */
@Composable
fun AboutDialog(lines: List<AboutLine>, onDismiss: () -> Unit) {
    DialogCard(onDismiss = onDismiss) {
        DialogTitle("About")
        Spacer(modifier = Modifier.height(26.dp))
        lines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = line.icon,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(17.dp))
                Column {
                    Text(text = line.title, fontSize = 19.5.sp, lineHeight = 24.sp, color = Color.Black)
                    Text(text = line.value, fontSize = 17.5.sp, lineHeight = 22.sp, color = Color.Black)
                }
            }
        }
        Spacer(modifier = Modifier.height(13.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            DialogAction("OK", onDismiss)
        }
    }
}

/** One of a handful of named options, chosen and closed. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    chosen: T,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onChoose: (T) -> Unit,
) {
    DialogCard(onDismiss = onDismiss) {
        DialogTitle(title)
        Spacer(modifier = Modifier.height(16.dp))
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChoose(option) }
                    .padding(start = 30.dp, top = 20.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Radio(selected = option == chosen)
                Spacer(modifier = Modifier.width(28.dp))
                Text(text = label(option), fontSize = 19.5.sp, lineHeight = 24.sp, color = Color.Black)
            }
        }
        Spacer(modifier = Modifier.height(35.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            DialogAction("OK", onDismiss)
        }
    }
}

/**
 * Drawn rather than taken from Material, whose radio button animates its dot in and needs a
 * colour scheme to say what "selected" means. Here it is a ring, and a filled ring.
 */
@Composable
private fun Radio(selected: Boolean) {
    Box(
        modifier = Modifier.size(20.dp).border(2.dp, Color.Black, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color.Black))
        }
    }
}

@Composable
private fun DialogAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 17.5.sp,
        lineHeight = 22.sp,
        color = Color.Black,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
