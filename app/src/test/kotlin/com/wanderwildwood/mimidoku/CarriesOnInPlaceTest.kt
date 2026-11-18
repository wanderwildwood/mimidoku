package com.wanderwildwood.mimidoku

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarriesOnInPlaceTest {

    @Test fun theBookInThePlayerCarriesOn() {
        assertTrue(carriesOnInPlace(true, Player.STATE_READY, false))
        assertTrue(carriesOnInPlace(true, Player.STATE_BUFFERING, false))
    }

    @Test fun aStoppedPlayerStillHoldingTheBookCarriesOn() {
        // Opening then prepares it again before playing.
        assertTrue(carriesOnInPlace(true, Player.STATE_IDLE, false))
    }

    @Test fun anotherBookIsLoaded() {
        assertFalse(carriesOnInPlace(false, Player.STATE_READY, false))
    }

    @Test fun anEndedOrFailedBookIsLoadedAgain() {
        assertFalse(carriesOnInPlace(true, Player.STATE_ENDED, false))
        assertFalse(carriesOnInPlace(true, Player.STATE_IDLE, true))
    }
}
