package com.wanderwildwood.mimidoku.lockscreen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemLockPlayerTest {
    @Test fun muditaOs1HasNoPlayerForOtherApps() {
        assertFalse(SystemLockPlayer.presentFor("MuditaOS K 1.6.1"))
        assertFalse(SystemLockPlayer.presentFor("MuditaOS K 1.0"))
    }

    @Test fun muditaOs2AndLaterHaveOne() {
        assertTrue(SystemLockPlayer.presentFor("MuditaOS K 2.0.0"))
        assertTrue(SystemLockPlayer.presentFor("MuditaOS K 10.1"))
    }

    @Test fun anyOtherAndroidHasOne() {
        assertTrue(SystemLockPlayer.presentFor("BP2A.250605.031"))
        assertTrue(SystemLockPlayer.presentFor(""))
    }
}
