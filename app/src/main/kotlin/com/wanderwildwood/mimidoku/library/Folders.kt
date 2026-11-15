package com.wanderwildwood.mimidoku.library

/** What the granted audiobook folders mean for the shelves, kept apart from the screens so it can be tested. */
object Folders {

    /**
     * Whether the card is read when the app opens.
     *
     * With a folder granted, always: books come and go while the app is closed. With none, only
     * while books from the card are still listed. Those were read from a folder the reader has
     * since stopped reading, they can no longer be opened, and a scan over no folders is what
     * takes them off the shelves.
     */
    fun scanOnOpen(folders: Int, cardBooks: Int): Boolean = folders > 0 || cardBooks > 0

    /**
     * The listed books that came from a folder which could not be read this time. They are kept
     * as they are, places and bookmarks with them, rather than taken off the shelves: the card
     * may be back tomorrow, or the reader may choose the folder again.
     */
    fun keptFrom(unreadableTrees: Collection<String>, bookUris: Collection<String>): List<String> =
        bookUris.filter { book -> unreadableTrees.any { tree -> book.startsWith("$tree/document/") } }

    /**
     * Whether a folder just chosen is the one that can no longer be read, now somewhere else: a
     * folder moved, or a card that comes back under another name. Then the old row goes, and its
     * books' places and bookmarks go to the same books in the new one, rather than the old row
     * staying unreadable forever next to a second copy of every book.
     *
     * With books kept from the old folder, every one of them must be found again in the new one
     * (by title and author): a folder that merely shares a few books is a different folder. With
     * none kept there is nothing to compare, and the folder's own name has to match.
     */
    fun replaces(oldTree: String, newTree: String, oldBooks: Int, notFound: Int): Boolean =
        if (oldBooks > 0) notFound == 0 else folderName(oldTree).equals(folderName(newTree), ignoreCase = true)

    /** The folder's own name out of a tree uri, the way the Audiobook folders row shows it. */
    fun folderName(tree: String): String {
        val id = tree.substringAfter("/tree/", "").substringBefore('/')
        val decoded = runCatching { java.net.URLDecoder.decode(id.replace("+", "%2B"), "UTF-8") }.getOrDefault(id)
        return decoded.substringAfterLast('/').substringAfterLast(':')
    }
}
