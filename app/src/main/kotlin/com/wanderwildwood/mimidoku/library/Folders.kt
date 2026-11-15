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
}
