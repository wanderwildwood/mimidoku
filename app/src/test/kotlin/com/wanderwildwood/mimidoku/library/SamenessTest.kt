package com.wanderwildwood.mimidoku.library

import com.wanderwildwood.mimidoku.library.Sameness.Spelling
import org.junit.Assert.assertEquals
import org.junit.Test

class SamenessTest {

    @Test
    fun `Moor spelled four ways is one shelf`() {
        val headings = Sameness.headings(
            listOf(
                Spelling("Basil Moor", onCard = true),
                Spelling("basil moor ", onCard = true),
                Spelling("Basil Moor,", onCard = false),
                Spelling("Basil  Moor", onCard = false),
                Spelling("Basil Moor", onCard = false),
            ),
        )
        assertEquals(listOf("Basil Moor"), headings)
    }

    @Test
    fun `O'Day with any apostrophe is one shelf`() {
        val headings = Sameness.headings(
            listOf(
                Spelling("Molly O'Day", onCard = true),
                Spelling("Molly O’Day", onCard = false),
                Spelling("Molly O‘Day", onCard = false),
                Spelling("Molly Oʼday", onCard = false),
                Spelling("Molly O`Day", onCard = false),
            ),
        )
        assertEquals(1, headings.size)
        assertEquals("Molly O'Day", headings.single())
    }

    @Test
    fun `the card's spelling wins over a server's when both are capitalised`() {
        val headings = Sameness.headings(
            listOf(
                Spelling("Bessel van der Kolk", onCard = false),
                Spelling("Bessel van der Kolk", onCard = false),
                Spelling("Bessel Van Der Kolk", onCard = true),
            ),
        )
        assertEquals(listOf("Bessel Van Der Kolk"), headings)
    }

    @Test
    fun `a capitalised spelling wins over a lower-case folder`() {
        val headings = Sameness.headings(
            listOf(
                Spelling("aldous huxley", onCard = true),
                Spelling("aldous huxley", onCard = true),
                Spelling("Aldous Huxley", onCard = false),
            ),
        )
        assertEquals(listOf("Aldous Huxley"), headings)
    }

    @Test
    fun `different people stay apart`() {
        val headings = Sameness.headings(
            listOf(Spelling("Basil Moor", true), Spelling("Basilia Moor", true), Spelling("Molly O'Day", true)),
        )
        assertEquals(3, headings.size)
    }

    @Test
    fun `keys fold what a reader would call the same`() {
        assertEquals(Sameness.key("Molly O'Day"), Sameness.key(" molly  o’day."))
        assertEquals(Sameness.key("Basil Moor"), Sameness.key("BASIL MOOR;"))
    }

    private data class B(val id: String, val title: String, val author: String?, val ms: Long, val kept: Boolean = true)

    private fun copies(vararg books: B) = Sameness.copies(
        books.toList(),
        title = { it.title },
        author = { it.author },
        lengthMs = { it.ms },
        toleranceMs = 30_000,
        prefer = compareByDescending<B> { it.kept }.thenBy { it.id },
    )

    @Test
    fun `the same book twice under one author is one book`() {
        val groups = copies(
            B("a", "The Copper Tide", "Basil Moor", 60_000_000),
            B("b", "the copper tide ", "basil moor", 60_010_000),
            B("c", "Winter Orchard", "Basil Moor", 40_000_000),
        )
        assertEquals(listOf(listOf("a", "b"), listOf("c")), groups.map { g -> g.map { it.id } })
    }

    @Test
    fun `two readings of one title stay two books`() {
        val groups = copies(
            B("a", "Winter Orchard", "Basil Moor", 40_000_000),
            B("b", "Winter Orchard", "Basil Moor", 46_000_000),
        )
        assertEquals(2, groups.size)
    }

    @Test
    fun `a book whose length is not known yet is nobody's copy`() {
        val groups = copies(
            B("a", "Winter Orchard", "Basil Moor", 40_000_000),
            B("b", "Winter Orchard", "Basil Moor", 0),
        )
        assertEquals(2, groups.size)
    }

    @Test
    fun `the same title by two authors is two books`() {
        val groups = copies(
            B("a", "Collected Stories", "Molly O'Day", 10_000_000),
            B("b", "Collected Stories", "Basil Moor", 10_000_000),
        )
        assertEquals(2, groups.size)
    }

    @Test
    fun `the preferred copy leads its group`() {
        val groups = copies(
            B("a", "Wren's Harbour", "Basil Moor", 9_000_000, kept = false),
            B("b", "Wren’s Harbour", "Basil Moor", 9_000_000, kept = true),
        )
        assertEquals("b", groups.single().first().id)
    }

    /**
     * The layout found on the Kompakt: one folder typed "basill moor", three books tagged
     * "Basil Moor" and six with no artist or "<unknown>" -- among them "02 The Lantern Field",
     * whose files disagree. It was two shelves; it is one, under the tag's spelling.
     */
    @Test
    fun `one author folder is one shelf named by its tags`() {
        val folder = "basill moor"
        val books = listOf(
            "The Salt Road 1" to "Basil Moor",
            "The Salt Road 2" to "Basil Moor",
            "02 The Lantern Field" to "Basil Moor",
            "00 A Year on the Shore" to null,
            "01 The Iron Bell" to "<unknown>",
            "03 The Copper Tide" to null,
            "04 Wren's Harbour" to "<unknown>",
            "The Long Coast - BBC Dramatisation" to null,
        )
        val heading = Sameness.folderHeading(books.map { folder }, books.map { it.second })
        assertEquals("Basil Moor", heading)
    }

    @Test
    fun `a book whose first file says unknown does not name the folder unknown`() {
        val heading = Sameness.folderHeading(listOf("basill moor", "basill moor"), listOf("<unknown>", null))
        assertEquals("basill moor", heading)
    }

    @Test
    fun `the folder name stands when nothing in it is tagged`() {
        assertEquals("Ada Fern", Sameness.folderHeading(listOf("Ada Fern", "Ada Fern"), listOf(null, null)))
    }

    @Test
    fun `placeholders are not names`() {
        assert(Sameness.isPlaceholder("<unknown>"))
        assert(Sameness.isPlaceholder(" Unknown Artist "))
        assert(Sameness.isPlaceholder(null))
        assert(!Sameness.isPlaceholder("Basil Moor"))
    }
}
