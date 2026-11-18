package com.wanderwildwood.mimidoku.data

/**
 * A book is known by its first file (see LibraryRepository's identity), so renaming that file,
 * or a new file sorting in front of it, makes the scan see a new book and a gone one. When the
 * new one holds files the gone one had, it is the same book, and the reader's place, bookmarks
 * and chapter marks go over to it rather than being deleted with the old one.
 */
object Renamed {

    /**
     * For each book new to this scan, the gone book it carries on: of the [going] books, the one
     * that held most of its files before. [newChapters] is each new book's file uris;
     * [ownerBefore] is which book each of those files belonged to before the scan. A gone book
     * is carried on by one new book at most.
     */
    fun pairs(
        newChapters: Map<String, List<String>>,
        ownerBefore: Map<String, String>,
        going: Set<String>,
    ): Map<String, String> {
        val taken = HashSet<String>()
        val out = LinkedHashMap<String, String>()
        for ((book, uris) in newChapters) {
            val best = uris.mapNotNull { ownerBefore[it] }
                .filter { it in going && it !in taken }
                .groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key ?: continue
            taken += best
            out[book] = best
        }
        return out
    }

    /**
     * Where each of the old book's files is in the new one: itself when it is still there; and
     * when exactly one old file is missing and exactly one new file is unknown, that pair, which
     * is a rename. Anything else has no place in the new book.
     */
    fun chapterMap(oldChapters: List<String>, newChapters: List<String>): Map<String, String> {
        val newSet = newChapters.toSet()
        val oldSet = oldChapters.toSet()
        val map = oldChapters.filter { it in newSet }.associateWith { it }.toMutableMap()
        val lostOld = oldChapters.filter { it !in newSet }
        val freshNew = newChapters.filter { it !in oldSet }
        if (lostOld.size == 1 && freshNew.size == 1) map[lostOld[0]] = freshNew[0]
        return map
    }
}
