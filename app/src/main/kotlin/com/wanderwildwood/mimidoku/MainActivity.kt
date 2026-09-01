package com.wanderwildwood.mimidoku

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.wanderwildwood.mimidoku.BuildConfig
import com.wanderwildwood.mimidoku.data.BookEntity
import com.wanderwildwood.mimidoku.data.ChapterEntity
import com.wanderwildwood.mimidoku.data.MarkEntity
import com.wanderwildwood.mimidoku.data.DurationReader
import com.wanderwildwood.mimidoku.data.LibraryRepository
import com.wanderwildwood.mimidoku.data.Preferences
import com.wanderwildwood.mimidoku.data.Shake
import com.wanderwildwood.mimidoku.data.Shelving
import com.wanderwildwood.mimidoku.library.TreeShape
import com.wanderwildwood.mimidoku.playback.CoverArt
import com.wanderwildwood.mimidoku.playback.PlaybackService
import com.wanderwildwood.mimidoku.ui.BookRow
import com.wanderwildwood.mimidoku.ui.BooksScreen
import com.wanderwildwood.mimidoku.ui.LibraryRow
import com.wanderwildwood.mimidoku.ui.LibraryScreen
import com.wanderwildwood.mimidoku.ui.MimidokuTheme
import com.wanderwildwood.mimidoku.ui.NowPlaying
import com.wanderwildwood.mimidoku.ui.Playback
import com.wanderwildwood.mimidoku.ui.PlaybackTools
import com.wanderwildwood.mimidoku.ui.PlayerScreen
import com.wanderwildwood.mimidoku.ui.AboutDialog
import com.wanderwildwood.mimidoku.ui.ChoiceDialog
import com.wanderwildwood.mimidoku.ui.Icons
import com.wanderwildwood.mimidoku.ui.BookmarkRow
import com.wanderwildwood.mimidoku.ui.ChapterRow
import com.wanderwildwood.mimidoku.ui.Chapters
import com.wanderwildwood.mimidoku.ui.FolderRow
import com.wanderwildwood.mimidoku.ui.FoldersScreen
import com.wanderwildwood.mimidoku.ui.BookmarksScreen
import com.wanderwildwood.mimidoku.ui.SearchScreen
import com.wanderwildwood.mimidoku.ui.SettingRow
import com.wanderwildwood.mimidoku.ui.SettingsScreen
import com.wanderwildwood.mimidoku.ui.StepperDialog
import com.wanderwildwood.mimidoku.ui.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MimidokuTheme {
                Mimidoku()
            }
        }
    }
}

/** Where the reader is. Shallow on purpose: everything here is one step from the library. */
private sealed interface Screen {
    data object Library : Screen
    data class Shelf(val name: String) : Screen
    data object Player : Screen
    data object Settings : Screen
    data object Search : Screen
    data object Bookmarks : Screen
    data object Folders : Screen
}

/** A preference the reader has opened, and is about to change or leave alone. */
private sealed interface Editing {
    data object Shelving : Editing
    data object Skip : Editing
    data object AutoRewind : Editing
    data object Sleep : Editing
    data object AutoSleepStart : Editing
    data object AutoSleepEnd : Editing
    data object Shake : Editing
    data object Speed : Editing
}

@Composable
private fun Mimidoku() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = remember { LibraryRepository(context) }
    val preferences = remember { Preferences.of(context) }

    // An hour is written the way the phone writes hours, so that a reader who has set a 24-hour
    // clock is not handed "10:00 PM" by this one app.
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    val clock = { minutesOfDay: Int -> timeFormat.format(dayAt(minutesOfDay)) }

    val books by library.books.collectAsState(initial = emptyList())

    var screen by remember { mutableStateOf<Screen>(Screen.Library) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var scanning by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<BookEntity?>(null) }
    var chapters by remember { mutableStateOf<List<ChapterEntity>>(emptyList()) }
    var chapterUri by remember { mutableStateOf<String?>(null) }
    var marks by remember { mutableStateOf<List<MarkEntity>>(emptyList()) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var query by remember { mutableStateOf("") }
    var announcement by remember { mutableStateOf<String?>(null) }
    // The sleep timer belongs to the service. These are the screen's copy of what it says.
    var sleepArmed by remember { mutableStateOf(false) }
    var sleepRemainingMs by remember { mutableStateOf(0L) }
    var sleepEventId by remember { mutableStateOf(0) }
    var showAbout by remember { mutableStateOf(false) }
    // The granted folders are held by the system, not by the app. This is the app's view of them,
    // refreshed whenever one is added or given up.
    var grants by remember { mutableStateOf(context.contentResolver.persistedUriPermissions.map { it.uri }) }
    var shapes by remember { mutableStateOf<Map<String, TreeShape>>(emptyMap()) }
    var locked by remember { mutableStateOf(false) }
    var chaptersOpen by remember { mutableStateOf(false) }

    // The controller is the activity's handle on the service's player. It is asynchronous: the
    // service may not be running yet when this screen appears.
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = future.get() }, MoreExecutors.directExecutor())
        onDispose {
            controller?.release()
            controller = null
        }
    }

    // Position is polled rather than observed: the player reports it continuously while playing,
    // and there is no callback that fires per second.
    LaunchedEffect(controller) {
        var sinceSaved = 0
        while (true) {
            controller?.let {
                isPlaying = it.isPlaying
                position = it.currentPosition

                // The timer's own state, kept by the service. A controller holds a copy of the
                // session's extras, so reading them here costs nothing across the binder.
                val extras = it.sessionExtras
                sleepArmed = extras.getBoolean(PlaybackService.SLEEP_ARMED)
                sleepRemainingMs = extras.getLong(PlaybackService.SLEEP_REMAINING)
                val said = extras.getInt(PlaybackService.SLEEP_EVENT_ID)
                if (said != sleepEventId) {
                    sleepEventId = said
                    extras.getString(PlaybackService.SLEEP_EVENT)?.let { word ->
                        announcement = word
                    }
                }

                duration = it.duration.takeIf { d -> d > 0 } ?: 0L
                chapterUri = it.currentMediaItem?.mediaId?.takeIf { id -> id.isNotEmpty() }

                // Written every few seconds rather than on every tick: often enough that killing
                // the app loses a moment rather than a chapter, rarely enough not to write to
                // storage ten times a minute.
                val book = playing
                if (book != null && it.isPlaying && ++sinceSaved >= SAVE_EVERY_TICKS) {
                    sinceSaved = 0
                    // mediaId rather than localConfiguration.uri: a MediaController is on the far
                    // side of a binder, and localConfiguration is documented as not surviving that
                    // trip. The id always does, which is why the chapters are given one.
                    it.currentMediaItem?.mediaId?.takeIf { id -> id.isNotEmpty() }?.let { chapter ->
                        library.rememberPosition(book.uri, chapter, it.currentPosition)
                    }
                }
            }
            delay(500)
        }
    }

    // Settings the reader chose last time mean nothing until there is a player to apply them to.
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        c.setPlaybackSpeed(preferences.speedTenths / 10f)
        c.ask(PlaybackService.VOLUME_BOOST, preferences.volumeBoosted)
        c.ask(PlaybackService.SKIP_SILENCE, preferences.skipSilence)
    }

    // The service outlives the screen, so the app can be opened onto a book that is already
    // playing. When it is, the strip at the bottom has to know about it without being told.
    LaunchedEffect(chapterUri) {
        val chapter = chapterUri ?: return@LaunchedEffect
        if (playing != null) return@LaunchedEffect
        val book = library.bookOfChapter(chapter) ?: return@LaunchedEffect
        playing = book
        chapters = library.chaptersOf(book.uri)
        marks = library.marksOf(book.uri)
    }

    // And when nothing is playing at all, the strip offers back whatever was being read last:
    // opening the app and pressing one button is the whole of what most sessions are.
    LaunchedEffect(books.isEmpty()) {
        if (playing != null || books.isEmpty()) return@LaunchedEffect
        val book = library.lastRead() ?: return@LaunchedEffect
        if (playing != null) return@LaunchedEffect
        playing = book
        chapters = library.chaptersOf(book.uri)
        // Its marks as well as its files. Without them a book that is one long recording is
        // offered back with no chapters at all, and the way into its chapter list - the line
        // naming what is playing - is not drawn.
        marks = library.marksOf(book.uri)
    }

    // An announcement is a thing the app said, not a thing it is saying. It goes away on its own.
    LaunchedEffect(announcement) {
        if (announcement == null) return@LaunchedEffect
        delay(ANNOUNCEMENT_MS)
        announcement = null
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Without taking the permission, the grant dies with this activity.
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        grants = context.contentResolver.persistedUriPermissions.map { it.uri }
        scanning = true
    }

    // A folder the reader granted stays granted, so the card is re-read every time the app opens:
    // books get added and removed while the app is not running, and the reader should not have to
    // ask for a scan they never asked to skip.
    LaunchedEffect(Unit) {
        if (grants.isNotEmpty()) scanning = true
    }

    // Reading a tree over SAF is disk work and belongs off the main thread.
    LaunchedEffect(scanning) {
        if (!scanning) return@LaunchedEffect
        if (grants.isEmpty()) {
            scanning = false
            return@LaunchedEffect
        }
        shapes = withContext(Dispatchers.IO) { library.rescan(grants) }
        scanning = false
    }

    // Lengths are read after the library is on screen, and go on being read for as long as the app
    // is open. Cancelling with the screen is the point: this is work that can always be resumed.
    LaunchedEffect(scanning) {
        if (scanning) return@LaunchedEffect
        DurationReader(context).run()
    }

    // What is left of the book, not of the chapter: the strip is there to say how much reading is
    // still in front of you, and a chapter boundary is not something the reader chose.
    val remaining = remember(chapters, chapterUri, position, duration, playing) {
        // Where the reader is comes from the player when there is one, and from what was written
        // down when there is not — a book offered back after a cold start has no player yet.
        val at = chapters.indexOfFirst { it.uri == (chapterUri ?: playing?.currentChapterUri) }
        val here = if (chapterUri != null) position else playing?.positionMs ?: 0L
        val chapterLength = if (chapterUri != null && duration > 0) {
            duration
        } else {
            chapters.getOrNull(at)?.durationMs ?: 0L
        }
        if (at < 0) (chapterLength - here).coerceAtLeast(0)
        else chapters.drop(at + 1).sumOf { it.durationMs.coerceAtLeast(0) } +
            (chapterLength - here).coerceAtLeast(0)
    }
    // A book is read in parts. Usually a part is a file; for a book delivered as one long
    // recording it is a mark inside that file. Everything downstream treats them the same, so
    // neither kind of book needs its own transport, its own list or its own label.
    val parts = remember(chapters, marks) {
        if (marks.isNotEmpty()) {
            marks.map { Part(it.title, it.chapterUri, it.startMs) }
        } else {
            chapters.map { Part(it.name.withoutExtension(), it.uri, 0L) }
        }
    }
    val startsAt = remember(parts, chapters) {
        // Where a part begins in the whole book: everything before its file, plus its own offset.
        var before = 0L
        val ends = chapters.associate { chapter ->
            val at = before
            before += chapter.durationMs.coerceAtLeast(0)
            chapter.uri to at
        }
        parts.map { (ends[it.chapterUri] ?: 0L) + it.startMs }
    }
    val atPart = remember(parts, chapterUri, position, playing) {
        // A book opened but not yet started has nothing prepared, so chapterUri is null and no
        // part matches - which left this at -1, emptied the chapter name, and took away the only
        // way into the chapter list: the book looked as though it had no chapters at all until
        // you pressed play. Unprepared, the book itself is the one that knows where it is.
        val inPart = chapterUri ?: playing?.currentChapterUri
        val into = if (chapterUri != null) position else playing?.positionMs ?: 0L
        parts.indexOfLast { it.chapterUri == inPart && it.startMs <= into }
            .takeIf { it >= 0 }
            ?: parts.indexOfFirst { it.chapterUri == inPart }.coerceAtLeast(0)
    }
    val goToPart = { index: Int ->
        parts.getOrNull(index)?.let { part ->
            val at = chapters.indexOfFirst { it.uri == part.chapterUri }
            if (at >= 0) controller?.seekTo(at, part.startMs)
        }
        Unit
    }

    val nowPlaying = playing?.let {
        NowPlaying(title = it.shownTitle(), remaining = remaining.asClock(), isPlaying = isPlaying)
    }
    // Coming back after a pause, playback steps back a little first: the last few seconds before
    // a reader stops listening are the ones they did not take in.
    val playPause: () -> Unit = {
        val c = controller
        val book = playing
        if (c != null && book != null && c.mediaItemCount == 0) {
            // Offered back rather than playing: the player has never been given this book.
            scope.launch {
                if (chapters.isEmpty()) {
                    chapters = library.chaptersOf(book.uri)
                    marks = library.marksOf(book.uri)
                }
                c.play(context, chapters, book)
            }
        } else if (c != null) {
            if (c.isPlaying) {
                c.pause()
            } else {
                val back = preferences.autoRewindSeconds * 1_000L
                if (back > 0) c.seekTo((c.currentPosition - back).coerceAtLeast(0))
                c.play()
            }
        }
    }

    // A book offered back after the app was closed has not been handed to the player yet. On the
    // library that does not matter - the strip asks the database where the reader was - but the
    // playback screen is the player's own screen, and until it has the book every control on it
    // does nothing at all. So opening it hands the book over, without starting it.
    LaunchedEffect(screen, controller, playing, chapters) {
        val c = controller ?: return@LaunchedEffect
        val book = playing ?: return@LaunchedEffect
        if (screen != Screen.Player || chapters.isEmpty()) return@LaunchedEffect
        if (c.mediaItemCount > 0) return@LaunchedEffect
        c.load(context, chapters, book)
    }

    // The hardware key means exactly what that screen's cross means, and a reader who presses it
    // on the library should leave the app rather than be held there.
    BackHandler(enabled = chaptersOpen) { chaptersOpen = false }

    BackHandler(enabled = screen != Screen.Library && !chaptersOpen) {
        screen = when (screen) {
            Screen.Bookmarks -> Screen.Player
            Screen.Folders -> Screen.Settings
            else -> Screen.Library
        }
    }

    when (val current = screen) {
        Screen.Library -> {
            // A shelf is whatever the reader chose to group by. Books that cannot answer — no
            // author, no genre — are left off rather than filed under a made-up heading.
            val shelves = remember(books, preferences.shelving) {
                books.mapNotNull { it.shelf(preferences.shelving) }
                    .collateIgnoringCase()
                    .sortedWith(String.CASE_INSENSITIVE_ORDER)
                    .map { LibraryRow(title = it, id = it) }
            }
            LibraryScreen(
                rows = shelves,
                status = when {
                    scanning -> "Scanning library…"
                    books.isEmpty() -> "No books yet"
                    else -> null
                },
                nowPlaying = nowPlaying,
                onRowClick = { screen = Screen.Shelf(it.title) },
                onSearchClick = { query = ""; screen = Screen.Search },
                onSettingsClick = { screen = Screen.Settings },
                onNowPlayingClick = { screen = Screen.Player },
                onPlayPauseClick = playPause,
            )
        }

        is Screen.Shelf -> {
            val shelved = remember(books, current.name, preferences.shelving) {
                // Matched the same way the shelf was named, or a shelf collated from two
                // spellings would open holding only the books that used one of them.
                books.filter { it.shelf(preferences.shelving).equals(current.name, ignoreCase = true) }
                    .map { it.toRow() }
            }

            BooksScreen(
                shelf = current.name,
                books = shelved,
                nowPlaying = nowPlaying,
                onClose = { screen = Screen.Library },
                onBookClick = { row ->
                    val book = books.firstOrNull { it.uri == row.id } ?: return@BooksScreen
                    playing = book
                    screen = Screen.Player
                    scope.launch {
                        chapters = library.chaptersOf(book.uri)
                        marks = library.marksOf(book.uri)
                        controller?.play(context, chapters, book)
                    }
                },
                onNowPlayingClick = { screen = Screen.Player },
                onPlayPauseClick = playPause,
            )
        }

        Screen.Player -> {
            val book = playing
            if (book == null) {
                screen = Screen.Library
            } else {
                PlayerScreen(
                    playback = Playback(
                        author = book.shownAuthor(),
                        title = book.shownTitle(),
                        chapter = if (parts.size > 1) parts.getOrNull(atPart)?.title.orEmpty() else "",
                        positionMs = position,
                        durationMs = duration,
                        isPlaying = isPlaying,
                        announcement = announcement,
                        skipSeconds = preferences.skipSeconds,
                        sleepArmed = sleepArmed,
                        sleepRemaining = sleepRemainingMs.takeIf { sleepArmed }?.asMinutes(),
                        volumeBoosted = preferences.volumeBoosted,
                        skipSilence = preferences.skipSilence,
                        locked = locked,
                    ),
                    tools = PlaybackTools(
                        onClose = { screen = Screen.Library },
                        onSleepTimer = {
                            controller?.ask(PlaybackService.SLEEP_TIMER, !sleepArmed)
                        },
                        onVolume = {
                            preferences.volumeBoosted = !preferences.volumeBoosted
                            controller?.ask(PlaybackService.VOLUME_BOOST, preferences.volumeBoosted)
                            announcement =
                                if (preferences.volumeBoosted) "Volume boost on" else "Volume boost off"
                        },
                        onSpeed = { editing = Editing.Speed },
                        onSkipSilence = {
                            preferences.skipSilence = !preferences.skipSilence
                            controller?.ask(PlaybackService.SKIP_SILENCE, preferences.skipSilence)
                            announcement =
                                if (preferences.skipSilence) "Skip silence on" else "Skip silence off"
                        },
                        onBookmarks = { screen = Screen.Bookmarks },
                        onLock = {
                            locked = !locked
                            announcement = if (locked) "Controls locked" else "Controls unlocked"
                        },
                    ),
                    transport = Transport(
                        // Back to the start of this part, or to the one before it when the
                        // reader is already at the start - which is how every other player
                        // behaves and what the button is reached for in the dark.
                        onPreviousChapter = {
                            val into = position - (parts.getOrNull(atPart)?.startMs ?: 0L)
                            goToPart(if (into > RESTART_MS || atPart <= 0) atPart else atPart - 1)
                        },
                        onRewind = {
                            val skip = preferences.skipSeconds * 1_000L
                            controller?.let { it.seekTo((it.currentPosition - skip).coerceAtLeast(0)) }
                        },
                        onPlayPause = playPause,
                        onForward = {
                            val skip = preferences.skipSeconds * 1_000L
                            controller?.let { it.seekTo(it.currentPosition + skip) }
                        },
                        onNextChapter = { goToPart((atPart + 1).coerceAtMost(parts.size - 1)) },
                        onSeekTo = { controller?.seekTo(it) },
                    ),
                    chapters = Chapters(
                        // Where a chapter begins is where everything before it ended, which is
                        // what a reader means by "how far in is chapter nine".
                        rows = remember(parts, startsAt) {
                            parts.mapIndexed { index, part ->
                                ChapterRow(
                                    id = index,
                                    number = index + 1,
                                    name = part.title,
                                    startsAt = (startsAt.getOrNull(index) ?: 0L).asClock(),
                                )
                            }
                        },
                        playingIndex = atPart,
                        open = chaptersOpen,
                        onOpen = { chaptersOpen = true },
                        onPick = { row ->
                            goToPart(row.id)
                            chaptersOpen = false
                        },
                        onDismiss = { chaptersOpen = false },
                    ),
                )
            }
        }

        Screen.Search -> {
            // Matched on both the book and whoever wrote it, because a reader looking for a book
            // by author does not think of that as a different kind of search.
            val found = remember(books, query) {
                if (query.isBlank()) {
                    emptyList()
                } else {
                    books.filter {
                        it.shownTitle().contains(query, true) ||
                            it.shownAuthor()?.contains(query, true) == true
                    }.map { it.toRow() }
                }
            }
            val searchShelves = remember(books, preferences.shelving) {
                books.mapNotNull { it.shelf(preferences.shelving) }.distinct()
                    .sortedWith(String.CASE_INSENSITIVE_ORDER)
                    .map { LibraryRow(title = it, id = it) }
            }
            SearchScreen(
                query = query,
                shelves = searchShelves,
                found = found,
                nowPlaying = nowPlaying,
                onQueryChange = { query = it },
                onBack = { screen = Screen.Library },
                onShelfClick = { screen = Screen.Shelf(it.title) },
                onBookClick = { row ->
                    val book = books.firstOrNull { it.uri == row.id } ?: return@SearchScreen
                    playing = book
                    screen = Screen.Player
                    scope.launch {
                        chapters = library.chaptersOf(book.uri)
                        marks = library.marksOf(book.uri)
                        controller?.play(context, chapters, book)
                    }
                },
                onNowPlayingClick = { screen = Screen.Player },
                onPlayPauseClick = playPause,
            )
        }

        Screen.Bookmarks -> {
            val book = playing
            if (book == null) {
                screen = Screen.Library
            } else {
                val marks by library.bookmarks(book.uri).collectAsState(initial = emptyList())
                val now = System.currentTimeMillis()
                BookmarksScreen(
                    bookmarks = marks.map {
                        BookmarkRow(
                            id = it.id,
                            when_ = "${DateUtils.getRelativeTimeSpanString(it.createdAt, now, DateUtils.MINUTE_IN_MILLIS)}, " +
                                DateFormat.getTimeFormat(context).format(it.createdAt),
                            position = it.positionMs.asClock(),
                            automatic = it.automatic,
                        )
                    },
                    onClose = { screen = Screen.Player },
                    onGoTo = { row ->
                        val mark = marks.firstOrNull { it.id == row.id } ?: return@BookmarksScreen
                        val at = chapters.indexOfFirst { it.uri == mark.chapterUri }
                        if (at >= 0) controller?.seekTo(at, mark.positionMs)
                        screen = Screen.Player
                    },
                    onDelete = { row -> scope.launch { library.deleteBookmark(row.id) } },
                    onAdd = {
                        val chapter = chapterUri
                        if (chapter != null) {
                            scope.launch {
                                library.addBookmark(book.uri, chapter, position, automatic = false)
                            }
                        }
                    },
                )
            }
        }

        Screen.Folders -> {
            FoldersScreen(
                folders = grants.map { uri ->
                    FolderRow(
                        id = uri.toString(),
                        // The tail of the document id is what the reader called the folder; the
                        // rest is the provider's business.
                        name = uri.lastPathSegment.orEmpty().substringAfterLast('/')
                            .substringAfterLast(':').ifBlank { uri.toString() },
                        byAuthor = shapes[uri.toString()] == TreeShape.AuthorsThenBooks,
                    )
                },
                onBack = { screen = Screen.Settings },
                onScanNow = { scanning = true },
                onAdd = { pickFolder.launch(null) },
                onRemove = { row ->
                    // Giving the grant back is the removal: there is nowhere else the folder is
                    // written down, so it cannot come back out of step with what is permitted.
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            row.id.toUri(),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    grants = context.contentResolver.persistedUriPermissions.map { it.uri }
                    scanning = true
                },
            )
        }

        Screen.Settings -> {
            SettingsScreen(
                rows = buildList {
                    // How many folders, not a sentence about what the row is for: a row
                    // earns its second line by saying something that changes.
                    add(
                        SettingRow(
                            key = "folders",
                            title = "Audiobook folders",
                            value = when (grants.size) {
                                0 -> "None chosen yet"
                                1 -> "1 folder"
                                else -> "${grants.size} folders"
                            },
                        ),
                    )
                    add(SettingRow("shelving", "Library view", preferences.shelving.label))
                    add(SettingRow("skip", "Skip amount", "${preferences.skipSeconds} seconds"))
                    add(SettingRow("rewind", "Auto rewind", "${preferences.autoRewindSeconds} seconds"))
                    add(SettingRow("sleep", "Sleep timer duration", "${preferences.sleepMinutes} minutes"))
                    // Under the duration, because it is the other half of the same thing:
                    // how long the timer runs, and how hard you have to shake to keep it
                    // running when it is about to stop on you and you are still awake.
                    add(SettingRow("shake", "Shake sensitivity", preferences.shake.label))
                    // Last, with the two hours it governs. It is the one setting here that
                    // is a standing arrangement rather than a value, and it brings rows of
                    // its own, so it does not belong in the middle of a list of numbers.
                    add(
                        SettingRow(
                            key = "autosleep",
                            title = "Automatic sleep timer",
                            value = null,
                            toggle = preferences.autoSleep,
                        ),
                    )
                    // The hours only exist while the window does. Two rows saying when something
                    // that is switched off starts and ends are two rows of nothing.
                    if (preferences.autoSleep) {
                        // One line, two columns, indented under the switch that governs them:
                        // a start and an end are read together, and they exist only while it is on.
                        add(
                            SettingRow(
                                key = "autosleepstart",
                                title = "Starts at",
                                value = clock(preferences.autoSleepStart),
                                beside = SettingRow(
                                    key = "autosleepend",
                                    title = "Ends at",
                                    value = clock(preferences.autoSleepEnd),
                                ),
                                beneath = true,
                            )
                        )
                    }
                },
                onClose = { screen = Screen.Library },
                onAbout = { showAbout = true },
                onRowClick = { row ->
                    when (row.key) {
                        "folders" -> screen = Screen.Folders
                        "shelving" -> editing = Editing.Shelving
                        "skip" -> editing = Editing.Skip
                        "rewind" -> editing = Editing.AutoRewind
                        "sleep" -> editing = Editing.Sleep
                        // A switch is its own dialog: there is one other value and no question
                        // worth asking about it.
                        "autosleep" -> preferences.autoSleep = !preferences.autoSleep
                        "autosleepstart" -> editing = Editing.AutoSleepStart
                        "autosleepend" -> editing = Editing.AutoSleepEnd
                        "shake" -> editing = Editing.Shake
                    }
                },
            )
        }
    }

    if (showAbout) {
        AboutDialog(
            version = BuildConfig.VERSION_NAME,
            onDismiss = { showAbout = false },
        )
    }

    val close = { editing = null }
    when (editing) {
        null -> Unit
        Editing.Shelving -> ChoiceDialog(
            title = "Library view",
            options = Shelving.entries,
            chosen = preferences.shelving,
            label = { it.label },
            onDismiss = close,
            onChoose = { preferences.shelving = it },
        )
        Editing.Shake -> ChoiceDialog(
            title = "Shake sensitivity",
            options = Shake.entries,
            chosen = preferences.shake,
            label = { it.label },
            onDismiss = close,
            onChoose = { preferences.shake = it },
        )
        Editing.Skip -> StepperDialog(
            title = "Skip amount",
            initial = preferences.skipSeconds,
            range = 5..300 step 5,
            label = { "$it seconds" },
            onDismiss = close,
            onSet = { preferences.skipSeconds = it },
        )
        Editing.AutoRewind -> StepperDialog(
            title = "Auto rewind",
            initial = preferences.autoRewindSeconds,
            range = 0..30 step 1,
            label = { "$it seconds" },
            onDismiss = close,
            onSet = { preferences.autoRewindSeconds = it },
        )
        Editing.Speed -> StepperDialog(
            title = "Playback speed",
            initial = preferences.speedTenths,
            range = 5..30 step 1,
            label = { "${it / 10}.${it % 10}x" },
            live = true,
            onDismiss = close,
            onSet = {
                preferences.speedTenths = it
                controller?.setPlaybackSpeed(it / 10f)
            },
        )
        Editing.Sleep -> StepperDialog(
            title = "Sleep timer duration",
            initial = preferences.sleepMinutes,
            range = 5..120 step 5,
            label = { "$it minutes" },
            onDismiss = close,
            onSet = { preferences.sleepMinutes = it },
        )
        // Half hours, because that is how bedtimes are said. Every minute of the day would be
        // forty-eight times the presses to say the same thing.
        Editing.AutoSleepStart -> StepperDialog(
            title = "Starts at",
            initial = preferences.autoSleepStart,
            range = 0..(23 * 60 + 30) step 30,
            label = { clock(it) },
            onDismiss = close,
            onSet = { preferences.autoSleepStart = it },
        )
        Editing.AutoSleepEnd -> StepperDialog(
            title = "Ends at",
            initial = preferences.autoSleepEnd,
            range = 0..(23 * 60 + 30) step 30,
            label = { clock(it) },
            onDismiss = close,
            onSet = { preferences.autoSleepEnd = it },
        )
    }
}

/** What the files say, or what the folders say when the files say nothing. */
private fun BookEntity.shownTitle(): String = tagTitle ?: name

/**
 * The folder wins, and the tag only fills a gap.
 *
 * It was the other way round, and it made a tidy library look ransacked. Someone who files
 * audiobooks under a folder per author has already said who wrote each one, deliberately and
 * consistently. The ID3 tags in the same files have not been curated by anyone: on one shelf
 * they read "DT Suzuki" as "D. T. Suzuki", "Frank Herbert" as "Frank Herbert (audio)", and
 * "Bessel Van Der Kolk" with a trailing comma, and one stray file claims an artist of "(02".
 * Letting them override produced a list of authors in four spellings and one that does not
 * exist.
 *
 * Where there is no folder to go by -- audio loose in the chosen directory -- the tag is all
 * there is, and it is used.
 */
private fun BookEntity.shownAuthor(): String? = author ?: tagAuthor

/** Which shelf a book belongs on, which depends on what the reader asked to see. */
/**
 * One shelf per name, however the folders spell it. A card that has both "alan Watts" and
 * "Alan Watts" on it is one author with two folders, not two authors, and distinct() alone
 * puts them on separate shelves sitting next to each other -- which looks exactly like a bug
 * in the scan when it is really just what is on the card.
 *
 * A name that starts with a capital wins, and only then the one used by most books. Counting
 * first was the obvious rule and the wrong one: a tagged book carries "Aldous Huxley" while its
 * folder is called "aldous huxley", the folders outnumber the tags, and the library ends up
 * listing its authors in lower case. These are people's names.
 */
private fun List<String>.collateIgnoringCase(): List<String> =
    groupBy { it.lowercase() }
        .map { (_, spellings) ->
            spellings.groupingBy { it }.eachCount().entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> {
                        it.key.firstOrNull()?.isUpperCase() == true
                    }.thenByDescending { it.value }
                )
                .first().key
        }

private fun BookEntity.shelf(shelving: Shelving): String? = when (shelving) {
    Shelving.Author -> shownAuthor()
    Shelving.Genre -> genre
    Shelving.Status -> when {
        lastPlayedAt == null -> "Not started"
        else -> "Started"
    }
}

/** One part of a book: a file of its own, or a marked place inside a longer one. */
private data class Part(val title: String, val chapterUri: String, val startMs: Long)

/**
 * Far enough into a part that "previous" means the start of this one rather than the last one.
 */
private const val RESTART_MS = 3_000L

/** A chapter is named by its file, and the extension is not part of the name to a reader. */
private fun String.withoutExtension(): String = substringBeforeLast('.')


private fun BookEntity.toRow() = BookRow(
    id = uri,
    title = shownTitle(),
    author = shownAuthor(),
    duration = durationMs.takeIf { it > 0 }?.asClock(),
    // Only once the reader has actually started it. A row of zeroes against every book they have
    // not opened yet says nothing and reads as clutter.
    percent = if (lastPlayedAt != null && durationMs > 0) {
        "${(progressMs * 100 / durationMs).coerceIn(0, 100)}%"
    } else {
        null
    },
)

private suspend fun MediaController.play(
    context: android.content.Context,
    chapters: List<ChapterEntity>,
    book: BookEntity,
) {
    load(context, chapters, book)
    playWhenReady = true
}

/**
 * Hands the player a book and leaves it standing there.
 *
 * Until this has happened the player holds nothing, and everything on the playback screen that
 * moves through a book — the bar, the two skips, the chapter list — is asking an empty player to
 * move and being answered with nothing, over a screen that reads 0:00:00 of 0:00:00 because that
 * is all an empty player can say. Loading the book without starting it makes the screen true and
 * the controls work; pressing play stays the reader's to do.
 */
private suspend fun MediaController.load(
    context: android.content.Context,
    chapters: List<ChapterEntity>,
    book: BookEntity,
) {
    // Only the artwork is set here. Everything else the notification shows - the title, the
    // author - is read out of the file itself, and a field set on the item would override it.
    val cover = CoverArt.forBook(context, chapters.firstOrNull()?.uri)
    setMediaItems(
        chapters.map {
            MediaItem.Builder()
                .setMediaId(it.uri)
                .setUri(it.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setArtworkData(cover, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        .build()
                )
                .build()
        }
    )
    // Pick up where the reader left off, if they have been here before.
    val resumeAt = chapters.indexOfFirst { it.uri == book.currentChapterUri }
    if (resumeAt >= 0) seekTo(resumeAt, book.positionMs)
    prepare()
}

/** Long enough to read a three-word sentence, short enough not to become part of the screen. */
private const val ANNOUNCEMENT_MS = 2_500L

/** The sleep timer is shown the way a kitchen timer is: minutes and seconds, never hours. */
private fun Long.asMinutes(): String {
    val total = (this / 1000).coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** Minutes since midnight as a moment today, which is all a time formatter will take. */
private fun dayAt(minutesOfDay: Int): Date = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
    set(Calendar.MINUTE, minutesOfDay % 60)
}.time

/** Named commands the service understands but a MediaController has no vocabulary for. */
private fun MediaController.ask(action: String, on: Boolean) {
    sendCustomCommand(SessionCommand(action, Bundle.EMPTY), Bundle().apply { putBoolean(PlaybackService.ON, on) })
}

/** Half-second ticks, so ten of them is five seconds. */
private const val SAVE_EVERY_TICKS = 10

/** Hours only when there are hours, because most chapters do not have any. */
private fun Long.asClock(): String {
    if (this <= 0) return "0:00"
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
