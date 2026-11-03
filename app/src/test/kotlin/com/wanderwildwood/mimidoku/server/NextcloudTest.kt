package com.wanderwildwood.mimidoku.server

import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A Nextcloud folder of made-up books, over a WebDAV stand-in that answers the way Nextcloud does. */
class NextcloudTest {

    private lateinit var server: MockWebServer

    /** Paths under the user's files; a trailing slash is a folder. */
    private val files = listOf(
        "Audiobooks/",
        "Audiobooks/Ada Whitlock/",
        "Audiobooks/Ada Whitlock/The Kettle Year/",
        "Audiobooks/Ada Whitlock/The Kettle Year/Part 10.mp3",
        "Audiobooks/Ada Whitlock/The Kettle Year/Part 2.mp3",
        "Audiobooks/Ada Whitlock/The Kettle Year/cover.jpg",
        "Audiobooks/Pond Notes.m4b",
        "Audiobooks/Empty Folder/",
        "Documents/",
    )

    private fun multistatus(folder: String): String {
        val inside = files.filter {
            it == folder || (it.startsWith(folder) && it.removePrefix(folder).trimEnd('/').let { rest -> rest.isNotEmpty() && '/' !in rest })
        }
        val responses = inside.joinToString("") { path ->
            val href = "/remote.php/dav/files/ada-id/" + path.split('/').joinToString("/") { it.replace(" ", "%20") }
            val folderish = path.endsWith("/")
            "<d:response><d:href>$href</d:href><d:propstat><d:prop>" +
                (if (folderish) "<d:resourcetype><d:collection/></d:resourcetype>" else "<d:resourcetype/><d:getcontentlength>1234</d:getcontentlength>") +
                "</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
        }
        return """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">$responses</d:multistatus>"""
    }

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Authorization") != Credentials.basic("ada@example.org", "app-pass-1")) {
                    return MockResponse().setResponseCode(401)
                }
                val path = request.requestUrl!!.encodedPath
                if (request.method == "PROPFIND" && path == "/remote.php/dav/") {
                    return MockResponse().setResponseCode(207).setBody(
                        """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"><d:response><d:href>/remote.php/dav/</d:href><d:propstat><d:prop><d:current-user-principal><d:href>/remote.php/dav/principals/users/ada-id/</d:href></d:current-user-principal></d:prop></d:propstat></d:response></d:multistatus>""",
                    )
                }
                val prefix = "/remote.php/dav/files/ada-id/"
                if (request.method == "PROPFIND" && path.startsWith(prefix)) {
                    val folder = request.requestUrl!!.pathSegments.drop(4).filter { it.isNotEmpty() }.joinToString("/") { it } + "/"
                    val wanted = if (folder == "/") "" else folder
                    if (wanted.isNotEmpty() && wanted !in files) return MockResponse().setResponseCode(404)
                    return MockResponse().setResponseCode(207).setBody(multistatus(wanted))
                }
                return MockResponse().setResponseCode(405)
            }
        }
        server.start()
    }

    @After fun tearDown() = server.shutdown()

    private fun client(password: String = "app-pass-1", folder: String = "Audiobooks") = NextcloudClient(
        NextcloudAccount(server.url("/").toString(), "ada@example.org", password, folder),
        { id, args -> "said $id ${args.joinToString()}" },
    )

    @Test fun `folders of audio are books, filed under the folder above, a loose file is a book of one`() = runBlocking {
        val books = (client().books() as AbsResult.Success).value
        assertEquals(listOf("Ada Whitlock/The Kettle Year", "Pond Notes.m4b"), books.map { it.path })
        val kettle = books.first()
        assertEquals("The Kettle Year", kettle.title)
        assertEquals("Ada Whitlock", kettle.author)
        // Natural order, and the cover is not a chapter.
        assertEquals(listOf("Part 2.mp3", "Part 10.mp3"), kettle.tracks.map { it.name })
        val pond = books.last()
        assertEquals("Pond Notes", pond.title)
        assertNull(pond.author)
    }

    @Test fun `a refused password and a missing folder say so`() = runBlocking {
        assertTrue((client(password = "nope").books() as AbsResult.Failure).message.startsWith("said"))
        val missing = client(folder = "Nowhere").books() as AbsResult.Failure
        assertTrue(missing.message.endsWith("Nowhere"))
    }

    @Test fun `a chapter is fetched from the user's own files, with the Basic header`() = runBlocking {
        val c = client()
        c.books()
        val (rows, chapters) = NextcloudSync.rows((c.books() as AbsResult.Success).value)
        val book = rows.first()
        assertEquals("NC:Ada Whitlock/The Kettle Year", book.uri)
        assertEquals(NextcloudSync.SOURCE_NC, book.sourceType)
        assertTrue(!book.kept)
        val first = chapters.first { it.bookUri == book.uri && it.sortIndex == 0 }
        assertEquals("Part 2", first.name)
        val url = c.chapterUrl(book.uri, first.uri)!!.toHttpUrl()
        assertEquals(listOf("remote.php", "dav", "files", "ada-id", "Audiobooks", "Ada Whitlock", "The Kettle Year", "Part 2.mp3"), url.pathSegments)
        assertEquals("Authorization", c.authHeader().first)
        assertNull(c.chapterUrl("ABS:x", "ABS:x:1"))
    }

    @Test fun `a one-file book's chapter is not mistaken for the book`() {
        val (rows, chapters) = NextcloudSync.rows(listOf(NcBook("Pond Notes.m4b", "Pond Notes", null, listOf(NcTrack("Pond Notes.m4b", "Pond Notes.m4b", 1)))))
        assertTrue(rows.single().uri != chapters.single().uri)
        assertEquals("Pond Notes.m4b", NextcloudSync.filePathOf(chapters.single().uri))
    }

    @Test fun `both kinds of server book are server books`() {
        assertTrue(isServerSource(AbsSync.SOURCE_ABS))
        assertTrue(isServerSource(NextcloudSync.SOURCE_NC))
        assertTrue(!isServerSource("LOCAL"))
    }
}
