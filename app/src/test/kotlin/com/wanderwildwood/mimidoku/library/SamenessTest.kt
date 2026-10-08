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
     * The shape of a real card: author folders the reader named, holding books whose files
     * carry whatever artist tag they came with -- the author with a degree and the narrator
     * run on, a publisher's series name, the lecturer of a course, a co-author, the author
     * with "(audio)" after the name, a disc number that landed in the artist field. The folder
     * is the author; none of these renames the shelf.
     */
    @Test
    fun `a folder is named by the folder, not by its books' tags`() {
        val shelves = listOf(
            "Basil Moor" to listOf("Basil Moor, M.D./Pip Narrow", "Basil Moor, M.D./Pip Narrow"),
            "Fern Guides" to listOf("Fern Field Guides", "Fern Field Guides"),
            "The Long Lectures" to listOf("Professor Ada Fern"),
            "Molly O'Day" to listOf("Molly O'Day, Tom Quill"),
            "Wren Harbour" to listOf("Wren Harbour (audio)"),
            "Iris Vale" to listOf(null, null, null, "(02", "CD4"),
            "Lantern and Field" to listOf("R Lantern, D Field"),
        )
        for ((folder, tags) in shelves) {
            assertEquals(folder, Sameness.folderHeading(tags.map { folder }, tags))
        }
    }

    /**
     * Six books, six different tags, none of them twice: the commonest tag was whichever
     * sorted first, and a folder called "Tobias Quill" was shelved as "Quill, Tobias - editor".
     */
    @Test
    fun `a folder whose books all disagree keeps its own name`() {
        val tags = listOf(
            "T. Quill (Narr. Sam Quill)",
            "T.Quill",
            "The Lantern Field [Volume I]",
            "Tobias Quill  - BBC Radio",
            null,
            "Quill, Tobias - editor",
        )
        assertEquals("Tobias Quill", Sameness.folderHeading(tags.map { "Tobias Quill" }, tags))
    }

    @Test
    fun `a lower-case folder borrows capitals from a tag of the same name`() {
        val tags = listOf("Alan Moor", "Alan Moor", "Alan Moor", null)
        assertEquals("Alan Moor", Sameness.folderHeading(tags.map { "alan moor" }, tags))
    }

    @Test
    fun `a tag of the same name does not lower a capitalised folder`() {
        val tags = listOf("alan moor", "alan moor")
        assertEquals("Alan Moor", Sameness.folderHeading(tags.map { "Alan Moor" }, tags))
    }

    @Test
    fun `a misspelt folder is still the folder`() {
        // Different letters are a different key; the folder is what the reader filed, and the
        // fix for a misspelling is renaming the folder, not reading the tag over it.
        val tags = listOf("Philippa Moor", "Philippa Moor", "Philippa Moor")
        assertEquals("phillippa moor", Sameness.folderHeading(tags.map { "phillippa moor" }, tags))
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
