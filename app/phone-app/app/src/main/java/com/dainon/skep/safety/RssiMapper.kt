package com.dainon.skep.safety

/**
 * P5-W2 근접 게이지 — BLE 수신강도(RSSI, dBm)를 0~100% 게이지와 진동 구간으로 변환.
 * 순수 함수만 담아 실기기 없이 단위테스트(경계값) 가능하게 분리한다(특허 §5.6 근접 게이지부).
 */
object RssiMapper {
    /** 이보다 약하면 0%(먼 거리). 실내 잡음 하한 근사. */
    const val RSSI_FAR = -95
    /** 이보다 강하면 100%(바로 옆). 근접 상한 근사. */
    const val RSSI_NEAR = -45

    /** RSSI(dBm) → 0..100. 범위 밖은 클램프. */
    fun gaugePercent(rssi: Int): Int {
        val clamped = rssi.coerceIn(RSSI_FAR, RSSI_NEAR)
        return (clamped - RSSI_FAR) * 100 / (RSSI_NEAR - RSSI_FAR)
    }

    /** 게이지 % → 진동 구간 0(먼)~3(근접). 가까울수록 강도↑. */
    fun vibrationLevel(percent: Int): Int = when {
        percent >= 80 -> 3
        percent >= 55 -> 2
        percent >= 30 -> 1
        else -> 0
    }
}
