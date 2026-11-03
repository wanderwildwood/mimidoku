package com.wanderwildwood.mimidoku.server

import android.content.res.Resources
import com.wanderwildwood.mimidoku.R
import com.wanderwildwood.mimidoku.library.BookScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/** A Nextcloud and a folder in it, as typed: the address, the login name, an app password. */
data class NextcloudAccount(
    val server: String,
    val login: String,
    val password: String,
    /** The folder the books are in, inside the person's files ("Audiobooks"); blank for all of them. */
    val folder: String,
    /** The user id the files live under, once a listing has found it; blank until then. */
    val userId: String = "",
)

/** One file or folder a PROPFIND listed, its path relative to the books folder. */
data class DavEntry(val path: String, val isFolder: Boolean, val size: Long)

/** One audio file of a Nextcloud book. */
data class NcTrack(val path: String, val name: String, val size: Long)

/** A book in the folder: a folder of audio files, or one audio file on its own. */
data class NcBook(
    /** Its path inside the books folder: what identifies it, and what a card copy is matched by. */
    val path: String,
    val title: String,
    /** The folder above the book's own, when books are kept as Author/Title. */
    val author: String?,
    val tracks: List<NcTrack>,
)

/**
 * Books in a folder of a Nextcloud, over WebDAV (`remote.php/dav/files/<user>/<folder>/`).
 *
 * The sign-in is the one Recipes, Memo, Shelf and Pictures use: the server's address, the login
 * name and an app password, typed, checked by asking for the folder; HTTP Basic on every request.
 * Nothing to register and no browser. The listing walks the folder a level at a time (PROPFIND,
 * Depth 1), because Nextcloud refuses Depth infinity by default.
 *
 * Like an Audiobookshelf book, a Nextcloud book is fetched whole before it plays (see
 * [AbsDownloader]): the files are fetched with the same Basic header, never with the password in
 * a url.
 */
class NextcloudClient(
    private val account: NextcloudAccount,
    /** Words a failure for the reader: a string resource and its arguments. */
    private val say: (Int, Array<out Any>) -> String,
    private val http: OkHttpClient = defaultHttp,
) : BookFiles {

    constructor(account: NextcloudAccount, resources: Resources) :
        this(account, { id, args -> resources.getString(id, *args) })

    private val base: HttpUrl? = ServerAddress.withScheme(account.server).trimEnd('/').plus("/").toHttpUrlOrNull()

    /** The person's own id, which is not always the login name (an email address logs in too). */
    var userId: String? = account.userId.ifBlank { null }
        private set

    /** The books folder's address, once the user id is known. */
    private fun folderUrl(user: String): HttpUrl.Builder =
        base!!.newBuilder().addPathSegments("remote.php/dav/files").addPathSegment(user).apply {
            account.folder.trim('/', ' ').split('/').filter { it.isNotBlank() }.forEach { addPathSegment(it) }
        }

    private fun request(url: HttpUrl) = Request.Builder().url(url)
        .header("Authorization", Credentials.basic(account.login, account.password, Charsets.UTF_8))

    /** Signs in and looks at the folder: what Connect checks before anything is kept. */
    suspend fun check(): AbsResult<Int> = books().map { it.size }

    /** Every book in the folder and the folders under it. */
    suspend fun books(): AbsResult<List<NcBook>> = withContext(Dispatchers.IO) {
        try {
            if (base == null) return@withContext AbsResult.Failure(say(R.string.nextcloud_not_nextcloud, arrayOf()))
            val user = userId ?: whoAmI().also { userId = it }
            val root = folderUrl(user).addPathSegment("").build()
            val entries = mutableListOf<DavEntry>()
            var level = listOf("")
            var depth = 0
            while (level.isNotEmpty() && depth <= MAX_DEPTH && entries.size < MAX_ENTRIES) {
                val next = mutableListOf<String>()
                for (folder in level) {
                    val url = root.newBuilder().apply {
                        folder.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
                    }.addPathSegment("").build()
                    for (entry in propfind(url, root)) {
                        if (entry.path == folder) continue
                        entries += entry
                        if (entry.isFolder) next += entry.path
                    }
                }
                level = next
                depth++
            }
            AbsResult.Success(booksIn(entries))
        } catch (e: Refused) {
            AbsResult.Failure(say(R.string.nextcloud_refused, arrayOf()))
        } catch (e: Missing) {
            AbsResult.Failure(say(R.string.nextcloud_no_folder, arrayOf(account.folder)))
        } catch (e: NotDav) {
            AbsResult.Failure(say(R.string.nextcloud_not_nextcloud, arrayOf()))
        } catch (e: Answered) {
            AbsResult.Failure(say(R.string.server_answered, arrayOf(e.code)))
        } catch (e: IOException) {
            AbsResult.Failure(say(R.string.server_unreachable, arrayOf()))
        }
    }

    override fun chapterUrl(bookUri: String, chapterUri: String): String? {
        val path = NextcloudSync.filePathOf(chapterUri) ?: return null
        if (base == null) return null
        // No network here: this is asked on the download's own thread before anything is
        // fetched. The id was found by the listing and kept; the login name is the fallback.
        val user = userId ?: account.login
        return folderUrl(user).apply {
            path.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
        }.build().toString()
    }

    override fun authHeader(): Pair<String, String> =
        "Authorization" to Credentials.basic(account.login, account.password, Charsets.UTF_8)

    /**
     * The user id the files live under, from `current-user-principal`. Falls back to the login
     * name, which is the same thing on most servers.
     */
    private fun whoAmI(): String {
        val url = base!!.newBuilder().addPathSegments("remote.php/dav/").build()
        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>"""
        http.newCall(request(url).method("PROPFIND", body.toRequestBody(XML)).header("Depth", "0").build()).execute().use {
            when {
                it.code == 401 || it.code == 403 -> throw Refused()
                it.code == 404 -> throw NotDav()
                !it.isSuccessful -> throw Answered(it.code)
            }
            val text = it.body!!.string()
            val href = try {
                val doc = parse(text)
                (doc.getElementsByTagNameNS("DAV:", "current-user-principal").item(0) as? Element)
                    ?.getElementsByTagNameNS("DAV:", "href")?.item(0)?.textContent
            } catch (e: Exception) {
                throw NotDav()
            }
            return href?.let { url.resolve(it) }?.pathSegments?.lastOrNull { s -> s.isNotEmpty() } ?: account.login
        }
    }

    /** One folder, a level deep: its entries with paths relative to [root]. */
    private fun propfind(url: HttpUrl, root: HttpUrl): List<DavEntry> {
        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>"""
        http.newCall(request(url).method("PROPFIND", body.toRequestBody(XML)).header("Depth", "1").build()).execute().use {
            when {
                it.code == 401 || it.code == 403 -> throw Refused()
                it.code == 404 -> throw Missing()
                it.code != 207 && !it.isSuccessful -> throw Answered(it.code)
            }
            return try {
                entries(it.body!!.string(), root)
            } catch (e: Exception) {
                throw NotDav()
            }
        }
    }

    private class Refused : IOException()
    private class Missing : IOException()
    private class NotDav : IOException()
    private class Answered(val code: Int) : IOException()

    companion object {
        /** How deep under the books folder books are looked for: Author/Series/Book/Disc. */
        const val MAX_DEPTH = 5

        /** Enough for any audiobook collection, and a stop for a folder that is not one. */
        const val MAX_ENTRIES = 50_000

        private val XML = "application/xml; charset=utf-8".toMediaType()

        private val defaultHttp: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        private fun parse(text: String) =
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(text.byteInputStream())

        /** A multistatus answer as entries, paths relative to [root] and decoded. */
        internal fun entries(xml: String, root: HttpUrl): List<DavEntry> {
            val doc = parse(xml)
            val rootSegments = root.pathSegments.filter { it.isNotEmpty() }
            val responses = doc.getElementsByTagNameNS("DAV:", "response")
            return (0 until responses.length).mapNotNull { i ->
                val response = responses.item(i) as Element
                val href = response.getElementsByTagNameNS("DAV:", "href").item(0)?.textContent ?: return@mapNotNull null
                val segments = root.resolve(href.trim())?.pathSegments?.filter { it.isNotEmpty() } ?: return@mapNotNull null
                if (segments.size < rootSegments.size || segments.subList(0, rootSegments.size) != rootSegments) return@mapNotNull null
                DavEntry(
                    path = segments.drop(rootSegments.size).joinToString("/"),
                    isFolder = response.getElementsByTagNameNS("DAV:", "collection").length > 0,
                    size = response.getElementsByTagNameNS("DAV:", "getcontentlength").item(0)?.textContent?.trim()?.toLongOrNull() ?: 0L,
                )
            }
        }

        /**
         * The books in a folder tree. Every folder holding audio files is a book of those
         * files, in name order (natural: "Part 2" before "Part 10"), named after the folder,
         * and filed under the folder above it as the author when there is one. An audio file
         * loose in the books folder itself is a book of one file. A folder can be a book and
         * hold other books too, as on the card.
         */
        fun booksIn(entries: List<DavEntry>): List<NcBook> {
            val audio = entries.filter { !it.isFolder && it.path.substringAfterLast('.', "").lowercase() in BookScanner.AUDIO }
            val loose = audio.filter { !it.path.contains('/') }
            val inFolders = audio.filter { it.path.contains('/') }.groupBy { it.path.substringBeforeLast('/') }
            val books = mutableListOf<NcBook>()
            loose.forEach {
                books += NcBook(
                    path = it.path,
                    title = it.path.substringBeforeLast('.'),
                    author = null,
                    tracks = listOf(NcTrack(it.path, it.path, it.size)),
                )
            }
            inFolders.forEach { (folder, files) ->
                val parts = folder.split('/')
                books += NcBook(
                    path = folder,
                    title = parts.last(),
                    author = parts.getOrNull(parts.size - 2),
                    tracks = files.sortedWith(compareBy(naturalOrder) { it.path.substringAfterLast('/') })
                        .map { NcTrack(it.path, it.path.substringAfterLast('/'), it.size) },
                )
            }
            return books.sortedBy { it.path.lowercase() }
        }

        /** "Part 2" before "Part 10": runs of digits compare as numbers. */
        val naturalOrder: Comparator<String> = Comparator { a, b ->
            val split = Regex("(\\d+)")
            val pa = split.split(a.lowercase()).zip(split.findAll(a.lowercase()).map { it.value }.toList() + "")
            val pb = split.split(b.lowercase()).zip(split.findAll(b.lowercase()).map { it.value }.toList() + "")
            for ((x, y) in pa.zip(pb)) {
                val text = x.first.compareTo(y.first)
                if (text != 0) return@Comparator text
                val nx = x.second.toBigIntegerOrNull()
                val ny = y.second.toBigIntegerOrNull()
                val num = if (nx != null && ny != null) nx.compareTo(ny) else x.second.compareTo(y.second)
                if (num != 0) return@Comparator num
            }
            pa.size.compareTo(pb.size).takeIf { it != 0 } ?: a.compareTo(b)
        }
    }
}

private inline fun <T, R> AbsResult<T>.map(transform: (T) -> R): AbsResult<R> = when (this) {
    is AbsResult.Failure -> this
    is AbsResult.Success -> AbsResult.Success(transform(value))
}
