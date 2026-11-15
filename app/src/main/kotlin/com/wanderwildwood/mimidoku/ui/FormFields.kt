package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The typed fields of a form that is rows of a paged list, one field to a row.
 *
 * The keyboard takes more than half of this panel. The window is resized for it (adjustResize in
 * the manifest, imePadding where a window is not resized), so the bar stays at the top and the
 * list gives up the room; what is left holds about three rows. So the row being typed in comes to
 * the top of the page, where the next field, or the button after the last one, shows beneath it,
 * and the keyboard's own Next key goes to the next field without the keyboard being put away.
 *
 * [firstRow] is the list's index of the first field; the fields follow it one row each.
 */
internal class FormFields(
    private val list: LazyListState,
    private val scope: CoroutineScope,
    private val firstRow: Int,
    count: Int,
) {
    private val fields = List(count) { FocusRequester() }
    internal var typingIn by mutableStateOf<Int?>(null)
    internal var listHeight by mutableIntStateOf(0)

    /** On the list itself: its height is how the keyboard's coming and going is seen. */
    fun listModifier(): Modifier = Modifier.onSizeChanged { listHeight = it.height }

    /** On field number [field], counted from 0. */
    fun fieldModifier(field: Int): Modifier = Modifier
        .focusRequester(fields[field])
        .onFocusChanged {
            if (it.isFocused) typingIn = field else if (typingIn == field) typingIn = null
        }

    /** The keyboard's Next key in [field]: the next field's row is brought up, then takes the cursor. */
    fun next(field: Int) {
        if (field + 1 >= fields.size) return
        scope.launch {
            list.scrollToItem(firstRow + field + 1)
            withFrameNanos { }
            fields[field + 1].requestFocus()
        }
    }

    internal suspend fun follow() {
        snapshotFlow { listHeight to typingIn }.collect { (_, field) ->
            if (field != null) list.scrollToItem(firstRow + field)
        }
    }
}

@Composable
internal fun rememberFormFields(list: LazyListState, firstRow: Int, count: Int): FormFields {
    val scope = rememberCoroutineScope()
    val form = remember(list) { FormFields(list, scope, firstRow, count) }
    LaunchedEffect(form) { form.follow() }
    return form
}
