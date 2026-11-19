package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.mimidoku.R
import com.wanderwildwood.mimidoku.server.ServerAddress

/**
 * Home's Nextcloud account as this screen shows it. [user] and [server] (without https://) while
 * it is in use; [signedOut] while the reader chose it and Home is signed out.
 */
data class FromHome(val user: String, val server: String, val signedOut: Boolean)

/** What the reader typed, on its way to being a Nextcloud folder. */
data class NextcloudEntry(val address: String, val login: String, val password: String, val folder: String) {
    val isComplete: Boolean get() = address.isNotBlank() && login.isNotBlank() && password.isNotBlank()
}

/**
 * Books from a folder in a Nextcloud, beside the card's and Audiobookshelf's.
 *
 * Each field is a row of its own, so the list pages to every one of them on a short panel.
 * Four fields, typed: the address the reader opens Nextcloud at, their login name, an app
 * password made in Nextcloud, and the folder the books are in. No browser and nothing to
 * register, the same sign-in as the other apps that use a Nextcloud. Connecting reads the
 * folder once, so its books sit on the shelf, and like a server's they are downloaded whole
 * before they play.
 */
@Composable
fun NextcloudScreen(
    address: String,
    login: String,
    folder: String,
    connected: Boolean,
    bookCount: Int,
    kept: List<KeptBook>,
    isBusy: Boolean,
    status: String?,
    onBack: () -> Unit,
    onConnect: (NextcloudEntry) -> Unit,
    onSyncNow: () -> Unit,
    onCancel: () -> Unit,
    onGiveBack: (KeptBook) -> Unit,
    onForget: () -> Unit,
    /** Home holds an account this app may use: the row "Use the phone's Nextcloud account" shows. */
    homeAvailable: Boolean = false,
    /** Home's account, when the reader chose it; null while the app's own sign-in is in use. */
    fromHome: FromHome? = null,
    onUseHome: (folder: String) -> Unit = {},
    onConnectHome: (folder: String) -> Unit = {},
    onStopHome: () -> Unit = {},
) {
    if (fromHome != null) {
        HomeNextcloudScreen(
            fromHome = fromHome, folder = folder, connected = connected, bookCount = bookCount, kept = kept,
            isBusy = isBusy, status = status, onBack = onBack, onConnect = onConnectHome, onSyncNow = onSyncNow,
            onCancel = onCancel, onGiveBack = onGiveBack, onForget = onForget, onStop = onStopHome,
        )
        return
    }
    var typedAddress by rememberSaveable { mutableStateOf(address) }
    var typedLogin by rememberSaveable { mutableStateOf(login) }
    var typedFolder by rememberSaveable { mutableStateOf(folder) }
    // Held only while the screen is open; never saved with the screen's state.
    var typedPassword by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(connected) {
        if (!connected && address.isBlank()) {
            typedAddress = ""
            typedLogin = ""
            typedFolder = ""
            typedPassword = ""
        }
    }

    var why by remember { mutableStateOf<String?>(null) }
    val busyWhy = stringResource(R.string.server_busy_why)
    val incompleteWhy = stringResource(R.string.nextcloud_incomplete_why)
    var warned by remember { mutableStateOf(false) }
    val plainWhy = stringResource(R.string.nextcloud_plain_http_why)
    LaunchedEffect(isBusy, typedAddress, typedLogin, typedPassword, typedFolder) { why = null }
    LaunchedEffect(typedAddress) { warned = false }

    // The rows of the list: the words at 0, then the four fields, then Connect.
    val list = rememberLazyListState()
    val form = rememberFormFields(list, firstRow = 1, count = 4)
    val focus = LocalFocusManager.current
    val entered = NextcloudEntry(typedAddress, typedLogin, typedPassword, typedFolder)
    // Connect, and Done on the keyboard in the last field. Never greyed out: pressed too soon,
    // it says what is missing.
    val connect = {
        when {
            isBusy -> why = busyWhy
            !entered.isComplete -> why = incompleteWhy
            ServerAddress.plainHttpOutsideHome(entered.address) && !warned -> {
                warned = true
                why = plainWhy
            }
            else -> {
                why = null
                onConnect(entered)
                typedPassword = ""
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White).imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTopBar(
                title = stringResource(R.string.nextcloud_title),
                onBack = onBack,
                trailing = {
                    if (isBusy) BarText(stringResource(R.string.server_cancel), onCancel)
                    else if (connected) BarText(stringResource(R.string.server_sync_now), onSyncNow)
                },
            )

            PagedColumn(modifier = Modifier.weight(1f).then(form.listModifier()), state = list, showCutRow = true) {
                item {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp)) {
                        Said(stringResource(R.string.nextcloud_intro))
                        Spacer(modifier = Modifier.height(20.dp))
                        // Only while Home has an account for this app; it takes the folder typed below.
                        if (homeAvailable) {
                            PlainRow(stringResource(R.string.home_account_use)) {
                                if (isBusy) why = busyWhy else onUseHome(typedFolder.trim().trim('/'))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
                item {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                        TextFieldMMD(
                            modifier = Modifier.fillMaxWidth().then(form.fieldModifier(0)),
                            value = typedAddress,
                            onValueChange = { typedAddress = it },
                            label = { TextMMD(text = stringResource(R.string.server_address)) },
                            placeholder = { TextMMD(text = "cloud.example.org") },
                            singleLine = true,
                            enabled = !isBusy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { form.next(0) }),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                item {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                        TextFieldMMD(
                            modifier = Modifier.fillMaxWidth().then(form.fieldModifier(1)),
                            value = typedLogin,
                            onValueChange = { typedLogin = it },
                            label = { TextMMD(text = stringResource(R.string.nextcloud_login)) },
                            singleLine = true,
                            enabled = !isBusy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { form.next(1) }),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                item {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                        TextFieldMMD(
                            modifier = Modifier.fillMaxWidth().then(form.fieldModifier(2)),
                            value = typedPassword,
                            onValueChange = { typedPassword = it },
                            label = {
                                TextMMD(
                                    text = stringResource(
                                        if (connected) R.string.nextcloud_password_again else R.string.nextcloud_password,
                                    ),
                                )
                            },
                            singleLine = true,
                            enabled = !isBusy,
                            visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { form.next(2) }),
                            trailingIcon = {
                                BarText(stringResource(if (shown) R.string.nextcloud_hide else R.string.nextcloud_show)) { shown = !shown }
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                item {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                        TextFieldMMD(
                            modifier = Modifier.fillMaxWidth().then(form.fieldModifier(3)),
                            value = typedFolder,
                            onValueChange = { typedFolder = it },
                            label = { TextMMD(text = stringResource(R.string.nextcloud_folder)) },
                            placeholder = { TextMMD(text = stringResource(R.string.nextcloud_folder_example)) },
                            singleLine = true,
                            enabled = !isBusy,
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                            // Done is Connect; the keyboard goes, so the answer under the button shows.
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); connect() }),
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
                item {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                        ButtonMMD(
                            onClick = connect,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            TextMMD(
                                text = stringResource(if (connected) R.string.server_connect_again else R.string.server_connect),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }

                        why?.let {
                            Spacer(modifier = Modifier.height(16.dp))
                            Said(it)
                        }
                        status?.let {
                            Spacer(modifier = Modifier.height(16.dp))
                            Said(it)
                        }
                        if (status == null && connected && bookCount > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Said(pluralStringResource(R.plurals.nextcloud_books_in_folder, bookCount, bookCount))
                        }
                        Spacer(modifier = Modifier.height(if (kept.isEmpty()) 28.dp else 32.dp))
                    }
                }

                if (kept.isNotEmpty()) {
                    item {
                        TextMMD(
                            text = stringResource(R.string.server_on_this_phone),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.Black,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                        )
                    }
                    items(kept, key = { it.id }) { book ->
                        KeptLine(book = book, onGiveBack = { onGiveBack(book) })
                    }
                    item { Spacer(modifier = Modifier.height(28.dp)) }
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Said(stringResource(R.string.nextcloud_password_note))
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Said(stringResource(R.string.nextcloud_folder_note))
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Said(stringResource(R.string.server_privacy_note))
                        if (connected) {
                            Spacer(modifier = Modifier.height(32.dp))
                            ForgetRow(
                                onForget = onForget,
                                words = R.string.nextcloud_forget,
                                armedWords = R.string.nextcloud_forget_confirm,
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Said(text: String) {
    TextMMD(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        lineHeight = 22.sp,
        color = Color.Black,
    )
}

/**
 * The same screen while Home's account is in use: who, on which server, from Home; the folder;
 * and "Stop using it", which goes back to the app's own sign-in, empty. While Home is signed out
 * it says so and nothing more: no error, nothing to retry.
 */
@Composable
private fun HomeNextcloudScreen(
    fromHome: FromHome,
    folder: String,
    connected: Boolean,
    bookCount: Int,
    kept: List<KeptBook>,
    isBusy: Boolean,
    status: String?,
    onBack: () -> Unit,
    onConnect: (folder: String) -> Unit,
    onSyncNow: () -> Unit,
    onCancel: () -> Unit,
    onGiveBack: (KeptBook) -> Unit,
    onForget: () -> Unit,
    onStop: () -> Unit,
) {
    var typedFolder by rememberSaveable { mutableStateOf(folder) }
    var why by remember { mutableStateOf<String?>(null) }
    val busyWhy = stringResource(R.string.server_busy_why)
    LaunchedEffect(isBusy, typedFolder) { why = null }

    val list = rememberLazyListState()
    val form = rememberFormFields(list, firstRow = 1, count = 1)
    val focus = LocalFocusManager.current
    val connect = {
        if (isBusy) why = busyWhy else { why = null; onConnect(typedFolder.trim().trim('/')) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White).imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTopBar(
                title = stringResource(R.string.nextcloud_title),
                onBack = onBack,
                trailing = {
                    if (isBusy) BarText(stringResource(R.string.server_cancel), onCancel)
                    else if (connected) BarText(stringResource(R.string.server_sync_now), onSyncNow)
                },
            )

            PagedColumn(modifier = Modifier.weight(1f).then(form.listModifier()), state = list, showCutRow = true) {
                item {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp)) {
                        Said(
                            if (fromHome.signedOut) {
                                stringResource(R.string.home_account_signed_out, stringResource(R.string.home_account_kept))
                            } else {
                                stringResource(R.string.home_account_in_use, fromHome.user, fromHome.server)
                            },
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
                if (!fromHome.signedOut) {
                    item {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                            TextFieldMMD(
                                modifier = Modifier.fillMaxWidth().then(form.fieldModifier(0)),
                                value = typedFolder,
                                onValueChange = { typedFolder = it },
                                label = { TextMMD(text = stringResource(R.string.nextcloud_folder)) },
                                placeholder = { TextMMD(text = stringResource(R.string.nextcloud_folder_example)) },
                                singleLine = true,
                                enabled = !isBusy,
                                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); connect() }),
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                    item {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                            ButtonMMD(onClick = connect, modifier = Modifier.fillMaxWidth()) {
                                TextMMD(
                                    text = stringResource(if (connected) R.string.server_connect_again else R.string.server_connect),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            why?.let {
                                Spacer(modifier = Modifier.height(16.dp))
                                Said(it)
                            }
                            status?.let {
                                Spacer(modifier = Modifier.height(16.dp))
                                Said(it)
                            }
                            if (status == null && connected && bookCount > 0) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Said(pluralStringResource(R.plurals.nextcloud_books_in_folder, bookCount, bookCount))
                            }
                            Spacer(modifier = Modifier.height(if (kept.isEmpty()) 28.dp else 32.dp))
                        }
                    }
                }

                if (kept.isNotEmpty()) {
                    item {
                        TextMMD(
                            text = stringResource(R.string.server_on_this_phone),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.Black,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                        )
                    }
                    items(kept, key = { it.id }) { book ->
                        KeptLine(book = book, onGiveBack = { onGiveBack(book) })
                    }
                    item { Spacer(modifier = Modifier.height(28.dp)) }
                }

                if (!fromHome.signedOut) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Said(stringResource(R.string.nextcloud_folder_note))
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Said(stringResource(R.string.server_privacy_note))
                        Spacer(modifier = Modifier.height(32.dp))
                        PlainRow(stringResource(R.string.home_account_stop)) { if (isBusy) why = busyWhy else onStop() }
                        if (connected) {
                            ForgetRow(
                                onForget = onForget,
                                words = R.string.nextcloud_forget,
                                armedWords = R.string.nextcloud_forget_confirm,
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

/** A row of words that does one thing when pressed, as the Forget row is drawn. */
@Composable
private fun PlainRow(text: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        TextMMD(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp,
            color = Color.Black,
        )
    }
}
