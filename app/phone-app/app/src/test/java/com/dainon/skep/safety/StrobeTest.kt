package com.dainon.skep.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrobeTest {

    @Test fun toggles_every_tick() {
        assertTrue(Strobe.isOnAt(0))
        assertFalse(Strobe.isOnAt(1))
        assertTrue(Strobe.isOnAt(2))
        assertFalse(Strobe.isOnAt(3))
    }

    @Test fun interval_yields_about_4hz() {
        // 125ms on + 125ms off = 250ms 한 주기 = 4Hz.
        assertEquals(125L, Strobe.INTERVAL_MS)
    }
}
