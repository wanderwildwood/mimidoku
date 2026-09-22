package com.wanderwildwood.mimidoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.mimidoku.R
import kotlinx.coroutines.delay

/** What the reader typed, on its way to being a server. */
data class ServerEntry(val address: String, val key: String) {
    val isComplete: Boolean get() = address.isNotBlank() && key.isNotBlank()
}

/** A book that has been fetched, and what it is costing to keep. */
data class KeptBook(
    val id: String,
    val title: String,
    val author: String?,
    /** Null while it is still being added up. */
    val size: String?,
)

/**
 * Where an Audiobookshelf server is entered, and what has been taken from it.
 *
 * Two fields, because that is all it takes: an address and a key. Connecting reads the whole
 * catalogue in one go rather than asking as you browse, so the server's books sit on the shelf
 * beside the ones on the card and stay listed when the phone is nowhere near the network.
 *
 * What a server book cannot do is play. It is a book you can *fetch* — ten hours of audio over
 * the radio, on a phone like this one, is not listening, it is a battery running down — so a
 * server book is kept first and played afterwards, and the row that offers it says so.
 *
 * The books already fetched are listed here rather than marked on the shelf, because this is
 * where the question they answer gets asked: not "what can I read" but "what is this costing me",
 * which is a question about the phone and not about the library.
 */
@Composable
fun ServerScreen(
    address: String,
    key: String,
    bookCount: Int,
    kept: List<KeptBook>,
    isBusy: Boolean,
    status: String?,
    onBack: () -> Unit,
    onConnect: (ServerEntry) -> Unit,
    onSyncNow: () -> Unit,
    onGiveBack: (KeptBook) -> Unit,
    onForget: () -> Unit,
) {
    var typedAddress by rememberSaveable { mutableStateOf(address) }
    var typedKey by rememberSaveable { mutableStateOf(key) }

    // Seeded from what is stored once, and then the fields belong to whoever is typing — except
    // when the server has just been forgotten, where leaving the address and a boxful of dots on
    // screen under the word "Forgotten" says the opposite of what has happened.
    LaunchedEffect(address, key) {
        if (address.isBlank() && key.isBlank()) {
            typedAddress = ""
            typedKey = ""
        }
    }

    val connected = address.isNotBlank()

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Back,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp).clickable(onClick = onBack),
                )
                Spacer(modifier = Modifier.weight(1f))
                if (connected && !isBusy) {
                    TextMMD(
                        text = stringResource(R.string.server_sync_now),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = Color.Black,
                        modifier = Modifier.clickable(onClick = onSyncNow),
                    )
                }
            }

            TextMMD(
                text = stringResource(R.string.server_title),
                style = MaterialTheme.typography.titleLarge,
                lineHeight = 32.sp,
                color = Color.Black,
                modifier = Modifier.padding(start = 16.dp),
            )

            LazyColumnMMD(modifier = Modifier.weight(1f)) {
                item {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp)) {
                        TextFieldMMD(
                            modifier = Modifier.fillMaxWidth(),
                            value = typedAddress,
                            onValueChange = { typedAddress = it },
                            label = { TextMMD(text = stringResource(R.string.server_address)) },
                            placeholder = { TextMMD(text = "192.168.1.70:13378") },
                            singleLine = true,
                            enabled = !isBusy,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TextFieldMMD(
                            modifier = Modifier.fillMaxWidth(),
                            value = typedKey,
                            onValueChange = { typedKey = it },
                            label = { TextMMD(text = stringResource(R.string.server_api_key)) },
                            singleLine = true,
                            enabled = !isBusy,
                            visualTransformation = PasswordVisualTransformation(),
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        val entered = ServerEntry(typedAddress, typedKey)
                        ButtonMMD(
                            onClick = { onConnect(entered) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = entered.isComplete && !isBusy,
                        ) {
                            TextMMD(
                                text = stringResource(if (connected) R.string.server_connect_again else R.string.server_connect),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }

                        if (status != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            TextMMD(
                                text = status,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 22.sp,
                                color = Color.Black,
                            )
                        }

                        // Only when nothing has just been said. A sync that has this moment
                        // reported "87 books." does not need a second line underneath repeating
                        // it back; this is for coming to the screen later and wanting to know.
                        if (status == null && connected && bookCount > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            TextMMD(
                                text = pluralStringResource(R.plurals.server_books_on_server, bookCount, bookCount),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 22.sp,
                                color = Color.Black,
                            )
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
                        TextMMD(
                            text = stringResource(R.string.server_key_note),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = Color.Black,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        TextMMD(
                            text = stringResource(R.string.server_privacy_note),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = Color.Black,
                        )

                        if (connected) {
                            Spacer(modifier = Modifier.height(32.dp))
                            ForgetRow(onForget = onForget)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

/**
 * A book that has been fetched, what it is taking up, and the way to give it back.
 *
 * The same shape as a granted folder on the folders screen, because it is the same kind of thing:
 * something the reader brought in, which they may want to stop carrying. Giving one back asks
 * first — it is minutes of wifi to undo — and what it costs is on the row, because that is the
 * fact the decision turns on.
 */
@Composable
private fun KeptLine(book: KeptBook, onGiveBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 27.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            TextMMD(
                text = book.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextMMD(
                text = listOfNotNull(book.author, book.size).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Icons.Delete,
            contentDescription = stringResource(R.string.server_cd_give_back),
            tint = Color.Black,
            modifier = Modifier.size(24.dp).clickable(onClick = onGiveBack),
        )
    }
}

/**
 * The row asks first. Nothing on the server is touched; its books leave this phone's library, and
 * so does anything downloaded from it.
 */
@Composable
private fun ForgetRow(onForget: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    // Long enough to mean it, short enough that a row left armed does not stay dangerous.
    LaunchedEffect(armed) {
        if (armed) {
            delay(4000)
            armed = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (armed) { armed = false; onForget() } else armed = true }
            .padding(vertical = 12.dp),
    ) {
        TextMMD(
            text = if (armed) {
                stringResource(R.string.server_forget_confirm)
            } else {
                stringResource(R.string.server_forget)
            },
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp,
            color = Color.Black,
            fontWeight = if (armed) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
