package com.wanderwildwood.mimidoku.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Where a server is and what gets us in. */
data class AbsServer(val url: String, val token: String) {
    /** Typing a trailing slash is not a mistake anyone should be punished for. */
    val base: String get() = url.trimEnd('/')
}

sealed interface AbsResult<out T> {
    data class Success<T>(val value: T) : AbsResult<T>
    data class Failure(val message: String) : AbsResult<Nothing>
}

data class AbsLibrary(val id: String, val name: String, val mediaType: String)

/** One audio file of a book, as the server has it. */
data class AbsTrack(
    /** The server's own handle for the file. Stable across renames, which is the point of it. */
    val ino: String,
    val index: Int,
    val name: String,
    val durationMs: Long,
)

/** A place in a book the server already knows about, so nothing has to open a file to find it. */
data class AbsMark(val title: String, val startMs: Long)

data class AbsBook(
    val id: String,
    val title: String,
    val author: String?,
    val genre: String?,
    val tracks: List<AbsTrack>,
    val marks: List<AbsMark>,
)

/**
 * Talks to an Audiobookshelf server.
 *
 * Written against a real 2.35 server rather than against the documentation, because the two
 * disagree about the one endpoint that matters: a file is fetched from
 * `/api/items/<id>/file/<ino>/download`, and the `contentUrl` the server puts on every track --
 * the same path without `/download` -- is not a route and answers 404.
 *
 * Both ways of authenticating work and both were checked: a bearer header, and the same token as
 * a `?token=` query parameter. The header is used everywhere here. The query parameter is what
 * would be needed to hand a url straight to a player, and is deliberately not used, because a
 * token in a url is a token in a log.
 */
class AbsClient(private val server: AbsServer) {

    /** Whether the server is there and the key is good. Answers with its version. */
    suspend fun ping(): AbsResult<String> = get("/api/libraries").map { "reachable" }

    suspend fun libraries(): AbsResult<List<AbsLibrary>> = get("/api/libraries").map { body ->
        body.getJSONArray("libraries").objects().map {
            AbsLibrary(
                id = it.getString("id"),
                name = it.optString("name", "Library"),
                mediaType = it.optString("mediaType", "book"),
            )
        }
    }

    /**
     * Every book in a library, in one call.
     *
     * The listing is deliberately thin -- it carries no files and no chapters -- so each book has
     * to be asked about separately afterwards. That is one request per book, which on a home
     * network is a library-sized pause and the reason syncing reports progress.
     *
     * Books the server has flagged as missing are dropped here rather than shown and then failing
     * to play. A third of this library is in that state at the time of writing, and a book that
     * cannot be fetched is worse than a book that was never offered.
     */
    suspend fun bookIds(libraryId: String): AbsResult<List<String>> =
        get("/api/libraries/$libraryId/items?limit=10000").map { body ->
            body.getJSONArray("results").objects()
                .filterNot { it.optBoolean("isMissing", false) }
                .map { it.getString("id") }
        }

    /** One book, with its files and the chapter list the server worked out. */
    suspend fun book(id: String): AbsResult<AbsBook> =
        get("/api/items/$id?expanded=1").map { body ->
            val media = body.getJSONObject("media")
            val metadata = media.optJSONObject("metadata") ?: JSONObject()
            AbsBook(
                id = id,
                title = metadata.optString("title", "").ifBlank { "Untitled" },
                author = metadata.optString("authorName", "").ifBlank { null },
                // A list, of which the first is the one a shelf would file it under.
                genre = metadata.optJSONArray("genres")?.takeIf { it.length() > 0 }?.optString(0),
                tracks = media.optJSONArray("audioFiles").orEmpty().objects()
                    .filterNot { it.optBoolean("exclude", false) }
                    .map { file ->
                        AbsTrack(
                            ino = file.getString("ino"),
                            index = file.optInt("index", 1),
                            name = file.optJSONObject("metadata")?.optString("filename")
                                ?.ifBlank { null } ?: "Part ${file.optInt("index", 1)}",
                            durationMs = file.optDouble("duration", 0.0).seconds(),
                        )
                    }
                    .sortedBy { it.index },
                // Seconds, and fractional: the server keeps them the way ffprobe reports them.
                marks = media.optJSONArray("chapters").orEmpty().objects().map {
                    AbsMark(
                        title = it.optString("title", "").ifBlank { "—" },
                        startMs = it.optDouble("start", 0.0).seconds(),
                    )
                },
            )
        }

    /** Where a file actually is. Fetched with the header, never with the token in the url. */
    fun fileUrl(itemId: String, ino: String): String =
        "${server.base}/api/items/$itemId/file/$ino/download"

    fun coverUrl(itemId: String): String = "${server.base}/api/items/$itemId/cover"

    /** The one header every request carries. Shared with the downloader, which is not a client. */
    fun authHeader(): Pair<String, String> = "Authorization" to "Bearer ${server.token}"

    private suspend fun get(path: String): AbsResult<JSONObject> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(server.base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                // Not infinite: a server that accepts the connection and then says nothing would
                // otherwise wedge a sync for good.
                readTimeout = 30_000
                val (name, value) = authHeader()
                setRequestProperty(name, value)
            }
            when (val code = connection.responseCode) {
                in 200..299 ->
                    AbsResult.Success(JSONObject(connection.inputStream.bufferedReader().readText()))
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                    AbsResult.Failure("The server did not accept that key.")
                HttpURLConnection.HTTP_NOT_FOUND ->
                    AbsResult.Failure("The server has no such thing.")
                else -> AbsResult.Failure("The server answered $code.")
            }
        } catch (e: IOException) {
            AbsResult.Failure("The server could not be reached.")
        } catch (e: org.json.JSONException) {
            // A wrong address usually reaches *something* -- a router, a different app -- and
            // what comes back is a web page rather than an error.
            AbsResult.Failure("That address answered with something that is not Audiobookshelf.")
        } finally {
            connection?.disconnect()
        }
    }
}

private inline fun <T, R> AbsResult<T>.map(transform: (T) -> R): AbsResult<R> = when (this) {
    is AbsResult.Failure -> this
    is AbsResult.Success -> AbsResult.Success(transform(value))
}

private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

/** The server keeps lengths in fractional seconds; everything here is milliseconds. */
private fun Double.seconds(): Long = (this * 1000).toLong()
