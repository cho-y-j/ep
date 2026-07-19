package com.dainon.skep.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P5-W0 저전력 전송 정책 순수 로직 — 묶음 flush 시점 + 서버 정책 적용(주기 전환).
 */
class WatchTelemetryPolicyTest {

    @Test
    fun defaultsAreGreenTenMinutes() {
        val p = WatchTelemetryPolicy()
        assertEquals("GREEN", p.state)
        assertEquals(600_000L, p.sendIntervalMs)
        assertEquals(60_000L, p.hrDutyMs)
    }

    @Test
    fun greenBuffersUntilIntervalElapsed() {
        val p = WatchTelemetryPolicy()
        assertFalse(p.shouldFlush(false, 3, 60_000))    // 1분 경과 < 10분 → 버퍼 유지.
        assertFalse(p.shouldFlush(false, 3, 599_000))   // 아직.
        assertTrue(p.shouldFlush(false, 3, 600_000))    // 10분 → 묶음 전송.
    }

    @Test
    fun emptyBufferNeverFlushes() {
        val p = WatchTelemetryPolicy()
        assertFalse(p.shouldFlush(false, 0, 999_999))
        assertFalse(p.shouldFlush(true, 0, 999_999))    // 실시간이라도 보낼 게 없으면 안 보냄.
    }

    @Test
    fun realtimeAlertFlushesImmediately() {
        val p = WatchTelemetryPolicy()
        assertTrue(p.shouldFlush(true, 1, 0))           // 🔴 경보 = 즉시.
    }

    @Test
    fun yellowPolicyShortensCycle() {
        val p = WatchTelemetryPolicy()
        p.apply("YELLOW", 300, 30)
        assertEquals("YELLOW", p.state)
        assertEquals(300_000L, p.sendIntervalMs)
        assertEquals(30_000L, p.hrDutyMs)
        assertFalse(p.shouldFlush(false, 2, 299_000))
        assertTrue(p.shouldFlush(false, 2, 300_000))    // 5분 → 전송.
    }

    @Test
    fun invalidPolicyValuesIgnored() {
        val p = WatchTelemetryPolicy()
        p.apply("PURPLE", 999_999, 0)                   // 범위 밖/미지원 → 기존 유지.
        assertEquals("GREEN", p.state)
        assertEquals(600_000L, p.sendIntervalMs)
        assertEquals(60_000L, p.hrDutyMs)
    }

    @Test
    fun nullPolicyFieldsKeepCurrent() {
        val p = WatchTelemetryPolicy()
        p.apply("YELLOW", null, null)
        assertEquals("YELLOW", p.state)                 // state 만 바뀌고 나머지 유지.
        assertEquals(600_000L, p.sendIntervalMs)
        assertEquals(60_000L, p.hrDutyMs)
    }

    @Test
    fun personalHrBandAppliedWhenValid() {
        val p = WatchTelemetryPolicy()
        assertNull(p.serverHrHigh)                       // 기본 미학습 → null(온디바이스 폴백).
        assertNull(p.serverHrLow)
        p.apply("GREEN", 600, 60, 55, 150)               // P5-W1 개인 대역.
        assertEquals(150, p.serverHrHigh)
        assertEquals(55, p.serverHrLow)
    }

    @Test
    fun invalidHrBandIgnored() {
        val p = WatchTelemetryPolicy()
        p.apply("GREEN", 600, 60, 55, 150)
        p.apply("GREEN", 600, 60, 10, 300)               // 범위 밖 → 기존 유지.
        assertEquals(150, p.serverHrHigh)
        assertEquals(55, p.serverHrLow)
        p.apply("GREEN", 600, 60, null, null)            // 누락 → 기존 유지(폴백 아님).
        assertEquals(150, p.serverHrHigh)
    }
}
