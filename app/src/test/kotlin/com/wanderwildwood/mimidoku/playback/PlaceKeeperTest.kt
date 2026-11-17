package com.wanderwildwood.mimidoku.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceKeeperTest {

    @Test
    fun aBookLoadedButNeverPlayedIsNotWritten() {
        val keeper = PlaceKeeper()
        assertNull(keeper.at("chapter-1", 90_000))
    }

    @Test
    fun aBookPlayingIsWritten() {
        val keeper = PlaceKeeper()
        keeper.started()
        assertEquals(PlaceKeeper.Place("chapter-1", 90_000), keeper.at("chapter-1", 90_000))
    }

    @Test
    fun theSamePlaceIsWrittenOnce() {
        val keeper = PlaceKeeper()
        keeper.started()
        keeper.at("chapter-1", 90_000)
        assertNull(keeper.at("chapter-1", 90_000))
        assertEquals(PlaceKeeper.Place("chapter-1", 95_000), keeper.at("chapter-1", 95_000))
    }

    @Test
    fun aPausedBookIsStillWrittenWhenTheReaderMoves() {
        val keeper = PlaceKeeper()
        keeper.started()
        keeper.at("chapter-1", 90_000)
        assertEquals(PlaceKeeper.Place("chapter-2", 0), keeper.at("chapter-2", 0))
    }

    @Test
    fun anotherBookIsNotWrittenUntilItPlays() {
        val keeper = PlaceKeeper()
        keeper.started()
        keeper.at("chapter-1", 90_000)
        keeper.loaded()
        assertNull(keeper.at("other-chapter-1", 0))
        keeper.started()
        assertEquals(PlaceKeeper.Place("other-chapter-1", 1_000), keeper.at("other-chapter-1", 1_000))
    }

    @Test
    fun noChapterMeansNothingToWrite() {
        val keeper = PlaceKeeper()
        keeper.started()
        assertNull(keeper.at(null, 90_000))
        assertNull(keeper.at("", 90_000))
    }

    @Test
    fun aPlaceBeforeTheStartIsWrittenAsTheStart() {
        val keeper = PlaceKeeper()
        keeper.started()
        assertEquals(PlaceKeeper.Place("chapter-1", 0), keeper.at("chapter-1", -40))
    }
}
