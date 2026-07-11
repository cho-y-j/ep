package com.dainon.skep.service

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sin

/**
 * FallDetector 합성 트레이스 유닛테스트.
 * enforce=true 로 verdict가 콜백을 직접 구동하도록 하여 CONFIRM/SUPPRESS를 검증한다.
 * (Shadow 모드는 impact 즉시 confirm이라 verdict 검증엔 부적합)
 */
class FallDetectorTest {

    private class Harness(enforce: Boolean, gyro: Boolean = true, pressure: Boolean = true) {
        var confirmed = 0
        var suppressed = 0
        val det = FallDetector(
            fallThreshold = 7.0f,
            impactThreshold = 18.0f,
            fallWindowMs = 1000L,
            gyroThreshold = 2.5f,
            pressureThreshold = 0.06f,
            stillThreshold = 1.0f,
            confirmMs = 2000L,
            gyroAvailable = gyro,
            pressureAvailable = pressure,
            enforce = enforce,
            onFallConfirmed = { confirmed++ },
            onFallSuppressed = { suppressed++ },
            log = { },
        )

        /** 가속도 구간 공급 (20ms = 50Hz) */
        fun accel(fromMs: Long, toMs: Long, mag: (Long) -> Float) {
            var t = fromMs
            while (t <= toMs) { det.onAccel(t, mag(t)); t += 20 }
        }
    }

    @Test
    fun freeFall_impact_still_isConfirmed() {
        val h = Harness(enforce = true)
        h.accel(0, 980) { 9.81f }                 // 안정
        h.det.onAccel(1000, 3.0f)                 // dip (자유낙하)
        h.accel(1020, 1180) { 5.0f }
        h.det.onGyro(1100, 4.0f)                  // 자유낙하 중 회전 (E_rot)
        longArrayOf(200, 400, 600, 800).forEach { h.det.onPressure(it, 1000.0f) }   // dip 전 기압
        h.det.onAccel(1200, 25.0f)                // impact (충격)
        longArrayOf(1400, 1600, 1800, 2000).forEach { h.det.onPressure(it, 1000.2f) } // 충격 후 상승 (E_alt)
        h.accel(1220, 3220) { 9.81f }             // 충격 후 정지 (E_still)

        assertEquals("낙상 확정(CONFIRM)", 1, h.confirmed)
        assertEquals("억제 없음", 0, h.suppressed)
    }

    @Test
    fun hammering_isSuppressed() {
        val h = Harness(enforce = true)
        h.accel(0, 980) { 9.81f }
        h.det.onAccel(1000, 5.0f)                 // dip → impact (망치 내리침)
        h.accel(1020, 1080) { 6.0f }
        longArrayOf(600, 1000, 1400).forEach { h.det.onGyro(it, 1.0f) }             // 회전 낮음
        longArrayOf(200, 400, 600, 800).forEach { h.det.onPressure(it, 1000.0f) }
        h.det.onAccel(1100, 22.0f)                // impact
        longArrayOf(1200, 1400, 1600, 1800, 2000).forEach { h.det.onPressure(it, 1000.0f) } // 기압 변화 없음
        // 충격 후에도 계속 진동 (정지 아님) — 망치질 반복
        h.accel(1120, 3120) { t -> if ((t / 100) % 2 == 0L) 4.0f else 20.0f }

        assertEquals("억제됨(SUPPRESS)", 1, h.suppressed)
        assertEquals("확정 없음", 0, h.confirmed)
    }

    @Test
    fun normalWalking_noTrigger() {
        val h = Harness(enforce = true)
        // 정상 보행: mag 8~13 (dip 임계 7 미만 없음) → 트리거 자체가 안 됨
        h.accel(0, 3000) { t -> 10.5f + 2.5f * sin(t / 200.0).toFloat() }

        assertEquals("트리거 없음(확정)", 0, h.confirmed)
        assertEquals("트리거 없음(억제)", 0, h.suppressed)
    }
}
