package com.wanderwildwood.mimidoku.library

import com.wanderwildwood.mimidoku.library.PlacesBackup.BackupException
import com.wanderwildwood.mimidoku.library.PlacesBackup.LibraryBook
import com.wanderwildwood.mimidoku.library.PlacesBackup.LibraryChapter
import com.wanderwildwood.mimidoku.library.PlacesBackup.SavedBook
import com.wanderwildwood.mimidoku.library.PlacesBackup.SavedBookmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class PlacesBackupTest {

    private val tree = "content://com.android.externalstorage.documents/tree/primary%3AAudiobooks/document/primary%3AAudiobooks%2F"

    private val saved = listOf(
        SavedBook(
            uri = tree + "Lighthouse",
            title = "The Lighthouse Keeper",
            author = "Ada Whitlock",
            chapterUri = tree + "Lighthouse%2F03.mp3",
            chapterIndex = 2,
            chapterName = "03",
            positionMs = 754_000,
            lastPlayedAt = 1_760_000_000_000,
            bookmarks = listOf(
                SavedBookmark(tree + "Lighthouse%2F01.mp3", 0, "01", 12_000, 1_759_000_000_000, false),
                SavedBookmark(tree + "Lighthouse%2F03.mp3", 2, "03", 700_000, 1_760_000_000_000, true),
            ),
        ),
        SavedBook(
            uri = tree + "Tides",
            title = "Tides of the North",
            author = "Tomas Reyes",
            chapterUri = null,
            chapterIndex = 0,
            chapterName = "",
            positionMs = 0,
            lastPlayedAt = null,
            bookmarks = listOf(SavedBookmark(tree + "Tides%2Fone.m4b", 0, "one", 5_000, 1_758_000_000_000, false)),
        ),
    )

    private fun chapters(book: String, vararg names: String) =
        names.mapIndexed { i, n -> LibraryChapter("$book%2F$n.mp3", i, n) }

    @Test
    fun writtenFileReadsBackTheSame() {
        val text = PlacesBackup.write(saved, Instant.parse("2026-11-03T12:00:00Z"))
        assertEquals(saved.sortedBy { it.uri }, PlacesBackup.read(text))
    }

    @Test
    fun somethingElseIsNotABackup() {
        expect(BackupException.Kind.NOT_A_BACKUP) { PlacesBackup.read("not json at all") }
        expect(BackupException.Kind.NOT_A_BACKUP) { PlacesBackup.read("""{"format":"fukuyaku-backup","version":1}""") }
    }

    @Test
    fun aNewerBackupIsRefused() {
        expect(BackupException.Kind.NEWER) { PlacesBackup.read("""{"format":"mimidoku-places","version":2,"books":[]}""") }
    }

    @Test
    fun aDamagedBackupIsRefusedWhole() {
        val good = PlacesBackup.write(saved)
        // A negative place in the second book: the first is good, and still nothing is returned.
        val bad = good.replace("\"position_ms\": 5000", "\"position_ms\": -1")
        assertTrue(bad != good)
        expect(BackupException.Kind.DAMAGED) { PlacesBackup.read(bad) }
        expect(BackupException.Kind.DAMAGED) { PlacesBackup.read("""{"format":"mimidoku-places","version":1}""") }
    }

    @Test
    fun booksAreFoundByAddressFirst() {
        val lighthouse = tree + "Lighthouse"
        val library = listOf(LibraryBook(lighthouse, "Something else entirely", null, chapters(lighthouse, "01", "02", "03")))
        val plan = PlacesBackup.plan(saved, library)
        assertEquals(listOf(PlacesBackup.Place(lighthouse, "$lighthouse%2F03.mp3", 754_000, 1_760_000_000_000)), plan.places)
        assertEquals(2, plan.bookmarks.getValue(lighthouse).size)
        assertEquals(1, plan.notFound)
        assertEquals(1, plan.books)
    }

    @Test
    fun aBookAtANewAddressIsFoundByTitleAndAuthor() {
        val elsewhere = "content://com.android.externalstorage.documents/tree/1234-ABCD%3ABooks/document/1234-ABCD%3ABooks%2FKeeper"
        val tides = "content://other/Tides"
        val library = listOf(
            LibraryBook(elsewhere, "the lighthouse keeper ", "ADA WHITLOCK", chapters(elsewhere, "01", "02", "03")),
            LibraryBook(tides, "Tides of the North", "Tomas Reyes", listOf(LibraryChapter("$tides/a", 0, "Part one"))),
        )
        val plan = PlacesBackup.plan(saved, library)
        // Chapter found by name in the moved book, by position in the one whose names changed.
        assertEquals("$elsewhere%2F03.mp3", plan.places.single().chapterUri)
        assertEquals(listOf("$tides/a"), plan.bookmarks.getValue(tides).map { it.chapterUri })
        assertEquals(0, plan.notFound)
        assertEquals(2, plan.books)
    }

    @Test
    fun twoBooksOfOneNameAreNotGuessedAt() {
        val library = listOf(
            LibraryBook("a", "The Lighthouse Keeper", "Ada Whitlock", chapters("a", "01")),
            LibraryBook("b", "The Lighthouse Keeper", "Ada Whitlock", chapters("b", "01")),
        )
        val plan = PlacesBackup.plan(saved.take(1), library)
        assertTrue(plan.places.isEmpty())
        assertTrue(plan.bookmarks.isEmpty())
        assertEquals(1, plan.notFound)
    }

    @Test
    fun aChapterThatIsNotThereIsLeftOut() {
        val lighthouse = tree + "Lighthouse"
        // Only one chapter now: the place in chapter three has nowhere to go; the bookmark in one does.
        val library = listOf(LibraryBook(lighthouse, "The Lighthouse Keeper", "Ada Whitlock", listOf(LibraryChapter("x", 0, "Intro"))))
        val plan = PlacesBackup.plan(saved.take(1), library)
        assertTrue(plan.places.isEmpty())
        assertEquals(listOf("x"), plan.bookmarks.getValue(lighthouse).map { it.chapterUri })
    }

    private fun expect(kind: BackupException.Kind, block: () -> Unit) {
        try {
            block()
            fail("expected $kind")
        } catch (e: BackupException) {
            assertEquals(kind, e.kind)
        }
    }
}
