package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
) {
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

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTopBar(
                title = stringResource(R.string.nextcloud_title),
                onBack = onBack,
                trailing = {
                    if (isBusy) BarText(stringResource(R.string.server_cancel), onCancel)
                    else if (connected) BarText(stringResource(R.string.server_sync_now), onSyncNow)
                },
            )

            PagedColumn(modifier = Modifier.weight(1f)) {
                item {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp)) {
                        Said(stringResource(R.string.nextcloud_intro))
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
                item {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                        TextFieldMMD(
                            modifier = Modifier.fillMaxWidth(),
                            value = typedAddress,
                            onValueChange = { typedAddress = it },
                            label = { TextMMD(text = stringResource(R.string.server_address)) },
                            placeholder = { TextMMD(text = "cloud.example.org") },
                            singleLine = true,
                            enabled = !isBusy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                item {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                        TextFieldMMD(
                            modifier = Modifier.fillMaxWidth(),
                            value = typedLogin,
                            onValueChange = { typedLogin = it },
                            label = { TextMMD(text = stringResource(R.string.nextcloud_login)) },
                            singleLine = true,
                            enabled = !isBusy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                item {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                        TextFieldMMD(
                            modifier = Modifier.fillMaxWidth(),
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
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
                            modifier = Modifier.fillMaxWidth(),
                            value = typedFolder,
                            onValueChange = { typedFolder = it },
                            label = { TextMMD(text = stringResource(R.string.nextcloud_folder)) },
                            placeholder = { TextMMD(text = stringResource(R.string.nextcloud_folder_example)) },
                            singleLine = true,
                            enabled = !isBusy,
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
                item {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                        val entered = NextcloudEntry(typedAddress, typedLogin, typedPassword, typedFolder)
                        ButtonMMD(
                            // Never greyed out: pressed too soon, it says what is missing.
                            onClick = {
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
                            },
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
