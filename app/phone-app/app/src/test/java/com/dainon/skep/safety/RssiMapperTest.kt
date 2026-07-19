package com.dainon.skep.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssiMapperTest {

    @Test fun gauge_boundaries() {
        assertEquals(0, RssiMapper.gaugePercent(RssiMapper.RSSI_FAR))    // -95 → 0%
        assertEquals(100, RssiMapper.gaugePercent(RssiMapper.RSSI_NEAR)) // -45 → 100%
    }

    @Test fun gauge_clamps_out_of_range() {
        assertEquals(0, RssiMapper.gaugePercent(-120))  // 하한 밖 → 0
        assertEquals(100, RssiMapper.gaugePercent(-10)) // 상한 밖 → 100
    }

    @Test fun gauge_midpoint_is_fifty() {
        val mid = (RssiMapper.RSSI_FAR + RssiMapper.RSSI_NEAR) / 2  // -70
        assertEquals(50, RssiMapper.gaugePercent(mid))
    }

    @Test fun gauge_is_monotonic() {
        assertTrue(RssiMapper.gaugePercent(-80) < RssiMapper.gaugePercent(-60))
    }

    @Test fun vibration_level_boundaries() {
        assertEquals(0, RssiMapper.vibrationLevel(0))
        assertEquals(0, RssiMapper.vibrationLevel(29))
        assertEquals(1, RssiMapper.vibrationLevel(30))
        assertEquals(1, RssiMapper.vibrationLevel(54))
        assertEquals(2, RssiMapper.vibrationLevel(55))
        assertEquals(2, RssiMapper.vibrationLevel(79))
        assertEquals(3, RssiMapper.vibrationLevel(80))
        assertEquals(3, RssiMapper.vibrationLevel(100))
    }
}
