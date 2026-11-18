package com.wanderwildwood.mimidoku.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAskTest {
    @Test fun askedOnceOnAndroid13AndLater() {
        assertTrue(NotificationAsk.needsAsking(sdk = 33, granted = false, askedBefore = false))
        assertTrue(NotificationAsk.needsAsking(sdk = 36, granted = false, askedBefore = false))
        assertFalse(NotificationAsk.needsAsking(sdk = 33, granted = false, askedBefore = true))
        assertFalse(NotificationAsk.needsAsking(sdk = 33, granted = true, askedBefore = false))
    }

    @Test fun neverOnTheKompakt() {
        assertFalse(NotificationAsk.needsAsking(sdk = 31, granted = false, askedBefore = false))
    }
}
