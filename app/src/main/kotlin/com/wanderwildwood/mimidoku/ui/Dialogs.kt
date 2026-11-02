package com.wanderwildwood.mimidoku.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.MaterialTheme
import com.mudita.mmd.components.text.TextMMD
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.mimidoku.R

@Composable
internal fun DialogTitle(text: String) {
    TextMMD(text = text, style = MaterialTheme.typography.titleLarge, lineHeight = 32.sp, color = Color.Black)
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
    EInkDialog(onDismiss = onDismiss) {
        DialogTitle(title)
        Spacer(modifier = Modifier.height(21.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clickable { change((value - range.step).coerceAtLeast(range.first)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Minus,
                    contentDescription = stringResource(R.string.dialog_cd_less),
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp),
                )
            }
            TextMMD(text = label(value), style = MaterialTheme.typography.headlineLarge, lineHeight = 36.sp, color = Color.Black)
            Box(
                modifier = Modifier.size(48.dp).clickable { change((value + range.step).coerceAtMost(range.last)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Plus,
                    contentDescription = stringResource(R.string.dialog_cd_more),
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp),
                )
            }
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
                DialogAction(stringResource(R.string.dialog_cancel), onDismiss)
                Spacer(modifier = Modifier.width(31.dp))
                DialogAction(stringResource(R.string.dialog_set)) { onSet(value); onDismiss() }
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
    EInkDialog(onDismiss = onDismiss) {
        DialogTitle(title)
        Spacer(modifier = Modifier.height(44.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            DialogAction(stringResource(R.string.dialog_cancel), onDismiss)
            Spacer(modifier = Modifier.width(31.dp))
            DialogAction(action) { onConfirm(); onDismiss() }
        }
    }
}

/** Something the reader should know, and one way out. */
@Composable
fun NoticeDialog(text: String, onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        DialogTitle(text)
        Spacer(modifier = Modifier.height(44.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            DialogAction(stringResource(R.string.dialog_ok), onDismiss)
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
    EInkDialog(onDismiss = onDismiss) {
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
                TextMMD(text = label(option), style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp, color = Color.Black)
            }
        }
        Spacer(modifier = Modifier.height(35.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            DialogAction(stringResource(R.string.dialog_ok), onDismiss)
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
internal fun DialogAction(text: String, onClick: () -> Unit) {
    // A 48 dp target around the word; the word stays where it was.
    Box(
        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp,
            color = Color.Black,
        )
    }
}

