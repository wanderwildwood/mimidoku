package com.wanderwildwood.mimidoku.shelf

import java.text.Normalizer
import java.util.Locale

/**
 * Whether a book in this library is the one Shelf (com.wanderwildwood.shoka) asks about.
 *
 * Shelf knows a printed book by its title and author as the Library of Congress or Open Library
 * gave them; this library knows a recording by its tags or its folder. So both are brought to a
 * plain form before they are compared: accents and case dropped, a leading "The", "A" or "An"
 * dropped, a subtitle after a colon dropped, and everything but letters and digits dropped.
 * Titles must then be the same. An author, where both have one, must share a surname: the last
 * word of Shelf's (which comes as "First Last" or "Last, First") among the words of ours.
 */
object ShelfMatch {

    private val MARKS = Regex("\\p{Mn}+")
    private val NOT_WORD = Regex("[^a-z0-9]+")
    private val ARTICLE = Regex("^(the|a|an) ")
    // A subtitle, and a bracketed note such as "(Unabridged)".
    private val SUBTITLE = Regex("\\s*[:;(\\[].*$")

    private fun plain(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKD).replace(MARKS, "").lowercase(Locale.ROOT)
            .replace('&', ' ').replace(NOT_WORD, " ").trim()

    fun title(s: String): String = plain(s.replace(SUBTITLE, "")).replace(ARTICLE, "").replace(" ", "")

    /** Shelf's author's surname, plain: "Le Guin, Ursula K." and "Ursula K. Le Guin" alike end in "guin". */
    fun surname(author: String): String? {
        val surnamePart = if (author.contains(',')) author.substringBefore(',') else author
        return plain(surnamePart).split(' ').lastOrNull { it.length > 1 }
    }

    fun matches(askedTitle: String, askedAuthor: String?, title: String, author: String?): Boolean {
        val want = title(askedTitle)
        if (want.isEmpty() || want != title(title)) return false
        val surname = askedAuthor?.let { surname(it) } ?: return true
        val ours = author?.let { plain(it).split(' ') } ?: return true
        return surname in ours
    }
}
