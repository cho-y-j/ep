package com.dainon.skep.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BaselineSyncPayload 키 계약 유닛테스트.
 * 서버 FieldSafetyController.BaselineRequest 필드를 전역 Jackson SNAKE_CASE 로 변환한 키와 정확히 일치해야 한다.
 * hr_rest_mean 누락 시 서버 400 BAD_BASELINE → 옛 camelCase 키 회귀 방지.
 */
class BaselineSyncPayloadTest {

    private fun sample() = BaselineSyncPayload.build(
        restHrMean = 72.0, restHrStd = 8.0, activeHrMean = 90.0,
        spo2Mean = 98.0, spo2Std = 1.5,
        bodyTempMean = 36.5, bodyTempStd = 0.3,
        accelBaselineMean = 9.81f, accelBaselineStd = 2.0f,
        alertSpo2Range = 4.0, samplesCount = 40,
    )

    @Test fun keys_match_server_snake_case_contract() {
        val expected = setOf(
            "hr_rest_mean", "hr_rest_std", "hr_active_mean",
            "spo2_mean", "spo2_std",
            "body_temp_mean", "body_temp_std",
            "accel_baseline_mean", "accel_baseline_std",
            "alert_spo2_range", "samples_count",
        )
        assertEquals(expected, sample().keys)
    }

    @Test fun required_hr_rest_mean_present_and_non_null() {
        // 서버 baselineSync 가드: hr_rest_mean == null → 400 BAD_BASELINE.
        val data = sample()
        assertTrue(data.containsKey("hr_rest_mean"))
        assertEquals(72.0, data["hr_rest_mean"])
    }

    @Test fun no_legacy_camelcase_keys() {
        // 회귀 방지: 서버가 조용히 무시하던 옛 키(camelCase·미대응 메타)가 남으면 안 된다.
        val keys = sample().keys
        assertTrue(keys.none { it.any(Char::isUpperCase) })
        listOf("restHrMean", "activeHrMean", "workerId", "timeSlot", "restSamples")
            .forEach { assertTrue("legacy key leaked: $it", !keys.contains(it)) }
    }
}
