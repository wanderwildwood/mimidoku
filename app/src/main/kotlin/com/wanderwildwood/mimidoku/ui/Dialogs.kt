package com.wanderwildwood.mimidoku.ui

import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.mimidoku.R

@Composable
private fun DialogTitle(text: String) {
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
            Icon(
                imageVector = Icons.Minus,
                contentDescription = stringResource(R.string.dialog_cd_less),
                tint = Color.Black,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { change((value - range.step).coerceAtLeast(range.first)) },
            )
            TextMMD(text = label(value), style = MaterialTheme.typography.headlineLarge, lineHeight = 36.sp, color = Color.Black)
            Icon(
                imageVector = Icons.Plus,
                contentDescription = stringResource(R.string.dialog_cd_more),
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

/**
 * What this is and what it is built out of.
 *
 * The borrowed parts are named here rather than buried in a file nobody opens: a typeface and an
 * icon set were made by other people under licences that ask to be credited, and crediting them
 * is a line of text.
 */
@Composable
fun AboutDialog(version: String, onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        DialogTitle(stringResource(R.string.about_title))
        Spacer(modifier = Modifier.height(20.dp))

        AboutText(stringResource(R.string.about_version, version))

        Spacer(modifier = Modifier.height(12.dp))
        AboutText(stringResource(R.string.about_licence))
        AboutText(stringResource(R.string.about_font))
        AboutText(stringResource(R.string.about_icons))

        Spacer(modifier = Modifier.height(14.dp))
        Llama()

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            DialogAction(stringResource(R.string.about_close), onDismiss)
        }
    }
}

@Composable
private fun AboutText(text: String, modifier: Modifier = Modifier.padding(start = 14.dp, end = 14.dp)) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        lineHeight = 21.sp,
        color = Color.Black,
        modifier = modifier,
    )
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
private fun DialogAction(text: String, onClick: () -> Unit) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        lineHeight = 22.sp,
        color = Color.Black,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * A llama at the foot of the About, which opens the page a donation goes to.
 * The site's address sits at the start of the same line, and only the llama and its words open it.
 *
 * Three words rather than an address: a verb and an object, so what happens when you press
 * them is not a surprise even though the page is not named. The drawing is his own, and it is
 * ink rather than an emoji, which is a colour glyph and reaches the panel as a pale smudge.
 *
 * The Kompakt may have nothing registered for a web address, so the intent is allowed to fail
 * quietly rather than take the dialog down with it.
 */
@Composable
private fun Llama() {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // One margin for the whole line rather than AboutText's own on each piece: padded
        // both sides, the address and the words did not fit on one line.
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        AboutText("wanderthe.dev", Modifier)
        Spacer(Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    // Straight to the checkout. The Donate button on the site only leads
                    // here anyway, so the page in between is a press the reader does not need.
                    // The short square.link form, not the long checkout.square.site address it
                    // redirects to -- the short one is what the site itself links to, so a
                    // regenerated checkout follows it and a published app does not break.
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://square.link/u/AGu8oT10")),
                        )
                    }.onFailure {
                        Toast.makeText(context, context.getString(R.string.about_no_browser), Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(vertical = 4.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.llama),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            AboutText(stringResource(R.string.about_feed_the_llamas), Modifier)
        }
    }
}
