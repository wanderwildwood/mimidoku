package com.wanderwildwood.mimidoku.library

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * When two names out of the files are the same name.
 *
 * Folders and tags are typed by different people on different machines, and the same author
 * turns up as "Basil Moor", "basil moor " and "Basil Moor," -- or as "Molly O'Day"
 * from a folder and "Molly O’Day" from a tag, the curly apostrophe a word processor puts in.
 * Case alone was folded before; these were not, and each one was a second shelf for the same
 * person. A non-breaking space, which looks exactly like a space and is not one, did the same.
 */
object Sameness {

    /** Every mark that is used as an apostrophe in a name. */
    private val APOSTROPHES = Regex("[’‘ʼ`´′]")

    /** Commas, full stops and the like left hanging at either end by a list or a byline. */
    private val LOOSE_ENDS = Regex("^[\\s,.;:]+|[\\s,.;:]+$")

    private val SPACES = Regex("\\s+")

    /**
     * A name as written, tidied but not changed: the spaces made ordinary and single, the
     * apostrophe made plain, and loose punctuation at the ends dropped. Its capitals stay -- this
     * is what is shown.
     */
    fun tidy(name: String): String =
        // NFKC turns a non-breaking space into a space and a ligature into its letters, and puts
        // an accent the same way however the file encoded it.
        Normalizer.normalize(name, Normalizer.Form.NFKC)
            .replace(APOSTROPHES, "'")
            .replace(SPACES, " ")
            .replace(LOOSE_ENDS, "")

    /** What two names are compared by: [tidy], and case folded. */
    fun key(name: String): String = tidy(name).lowercase(Locale.ROOT)

    /** One spelling of a name as the library has it, and whether it came from the card. */
    data class Spelling(val name: String, val onCard: Boolean)

    /**
     * One heading per name, however it is spelled.
     *
     * Of the spellings, one that starts with a capital wins first -- these are people's names, and
     * a folder called "aldous huxley" should not decide how a tagged "Aldous Huxley" is shown --
     * then the one the card uses, since the card is what the reader filed themselves and a server's
     * byline is somebody else's, then the one most books carry. The heading is the [tidy] form.
     */
    fun headings(spellings: List<Spelling>): List<String> =
        spellings.filter { key(it.name).isNotEmpty() }
            .groupBy { key(it.name) }
            .map { (_, all) ->
                all.groupBy { tidy(it.name) }.entries
                    .sortedWith(
                        compareByDescending<Map.Entry<String, List<Spelling>>> {
                            it.key.firstOrNull()?.isUpperCase() == true
                        }
                            .thenByDescending { entry -> entry.value.count { it.onCard } }
                            .thenByDescending { it.value.size }
                            .thenBy { it.key },
                    )
                    .first().key
            }

    /**
     * What a tagger writes when it knows nothing, which is not a name. Android's own media store
     * says "<unknown>" for a missing artist, and a file that passed through it can carry the
     * word as if it were one.
     */
    fun isPlaceholder(name: String?): Boolean =
        name == null || key(name).let { it.isEmpty() || it in PLACEHOLDERS }

    private val PLACEHOLDERS = setOf("<unknown>", "unknown", "unknown artist", "various artists", "various")

    /**
     * The heading for one author's folder.
     *
     * The folder decides which books belong together -- the reader put them there -- but not
     * how the author is spelled: a folder typed in a hurry as "basill moor" holding books
     * the publisher tagged "Basil Moor" is one author, and the tag is the better spelling.
     * So the name most of its tagged books carry, and the folder's own name only where none
     * of them is tagged. A book whose files disagree contributes one tag, the first real one.
     */
    fun folderHeading(folderNames: List<String>, tags: List<String?>): String {
        val real = tags.filterNot { isPlaceholder(it) }.filterNotNull()
        if (real.isEmpty()) return headings(folderNames.map { Spelling(it, onCard = true) }).first()
        val commonest = real.groupBy { key(it) }.values
            .sortedWith(compareByDescending<List<String>> { it.size }.thenBy { key(it.first()) })
            .first()
        return headings(commonest.map { Spelling(it, onCard = true) }).first()
    }

    /**
     * Copies of one book: the same title by the same author, and the same length near enough
     * ([toleranceMs]) that they are one recording rather than two readings of it.
     *
     * Returns one group per book, in the order the books first appear; the first of each group
     * is the one [prefer] ranks highest, and the rest are its copies. A book whose length is not
     * yet known is never a copy of anything -- the card's lengths are read in the background,
     * and guessing from the title alone would fold two recordings of one book together.
     */
    fun <T> copies(
        books: List<T>,
        title: (T) -> String,
        author: (T) -> String?,
        lengthMs: (T) -> Long,
        toleranceMs: Long,
        prefer: Comparator<T>,
    ): List<List<T>> {
        val groups = mutableListOf<MutableList<T>>()
        val byName = HashMap<Pair<String, String>, MutableList<MutableList<T>>>()
        for (book in books) {
            val length = lengthMs(book)
            if (length <= 0) {
                groups += mutableListOf(book)
                continue
            }
            val name = key(title(book)) to key(author(book).orEmpty())
            val candidates = byName.getOrPut(name) { mutableListOf() }
            val same = candidates.firstOrNull { group ->
                group.any { abs(lengthMs(it) - length) <= toleranceMs }
            }
            if (same != null) {
                same += book
            } else {
                val group = mutableListOf(book)
                candidates += group
                groups += group
            }
        }
        return groups.map { it.sortedWith(prefer) }
    }
}
