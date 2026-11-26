package com.wanderwildwood.mimidoku.library

import com.wanderwildwood.mimidoku.data.BookEntity
import com.wanderwildwood.mimidoku.data.SOURCE_LOCAL

/**
 * What the files say, or what the folders say when the files say nothing -- or when the files
 * say less.
 *
 * A folder of files names the book in its album tag, as a rule, and the tag is taken at its
 * word. But an album tag is sometimes the *series*: a card held "Basil Moor - The Second Marsh
 * Book, Volume Two" with every file in it tagged with the album "The Marsh Books", so the book
 * was listed under the series' name and could not be told from its sibling. Where the folder's
 * name contains the tag and says more besides -- more than a number in front and the author's
 * name, which a folder often carries -- the folder is the fuller name, and it is shown. A
 * server's title is a librarian's and is never second-guessed.
 */
internal fun BookEntity.shownTitle(): String {
    val tag = tagTitle ?: return nameTitle()
    if (sourceType != SOURCE_LOCAL) return tag
    val folder = Sameness.key(name)
    val album = Sameness.key(tag)
    if (folder == album || album.isEmpty() || !folder.contains(album)) return tag
    val rest = (author ?: Sameness.nameAuthor(name))?.let { folder.replace(Sameness.key(it), " ") }.orEmpty().ifEmpty { folder }
        .replace(album, " ")
        .replace(LEADING_NUMBER, "")
    return if (rest.any { it.isLetter() }) nameTitle() else tag
}

/**
 * The folder's or file's name as a title: without the author in front when the author shown
 * is the one split off it ("Kenneth Grahame - The Wind in the Willows" is "The Wind in the
 * Willows" by Kenneth Grahame). Only what is shown changes; the book is still found, kept and
 * remembered by its uri.
 */
internal fun BookEntity.nameTitle(): String = Sameness.titleAfterAuthor(name, shownAuthor())

/** "01 ", "1. ", "03 - ": the place in a series a folder is often numbered with, not its name. */
internal val LEADING_NUMBER = Regex("^\\d{1,3}[\\s._-]+")

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
 *
 * With neither, the book's own name may give one: "Ada Whitlock - Loose Named" ([Sameness.nameAuthor]).
 */
internal fun BookEntity.shownAuthor(): String? =
    author ?: tagAuthor?.takeUnless { Sameness.isPlaceholder(it) } ?: Sameness.nameAuthor(name)
