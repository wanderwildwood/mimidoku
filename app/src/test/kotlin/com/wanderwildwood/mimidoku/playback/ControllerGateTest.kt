package com.wanderwildwood.mimidoku.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerGateTest {

    private val own = "com.wanderwildwood.mimidoku"

    @Test fun theAppItselfIsAccepted() {
        assertTrue(ControllerGate.accepts(own, own, trusted = false, systemController = false))
        assertTrue(ControllerGate.mayUseNamedCommands(own, own))
    }

    @Test fun theSystemAndTrustedControllersAreAccepted() {
        assertTrue(ControllerGate.accepts(own, "com.android.systemui", trusted = true, systemController = false))
        assertTrue(ControllerGate.accepts(own, own, trusted = false, systemController = true))
    }

    @Test fun anyOtherAppIsTurnedAway() {
        assertFalse(ControllerGate.accepts(own, "com.example.curious", trusted = false, systemController = false))
    }

    @Test fun theNamedCommandsAreForThisAppAlone() {
        assertFalse(ControllerGate.mayUseNamedCommands(own, "com.android.systemui"))
    }
}
