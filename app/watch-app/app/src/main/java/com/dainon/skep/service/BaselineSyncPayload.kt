package com.dainon.skep.service

/**
 * 워치 → 서버 베이스라인 동기화(POST /api/field-auth/baseline/sync) 페이로드 순수 조립기.
 * 서버 계약: FieldSafetyController.BaselineRequest 를 전역 Jackson SNAKE_CASE 로 역직렬화.
 * 키가 계약과 다르면 서버가 조용히 무시 → hr_rest_mean 누락 시 400 BAD_BASELINE.
 * 실기기 없이 키 계약을 단위테스트 하도록 순수 함수로 분리(SosPayload 와 동일 패턴).
 * alert_hr_upper/alert_hr_lower 는 워치가 학습 베이스라인으로 보유하지 않음 → 미전송(계약상 nullable).
 */
object BaselineSyncPayload {
    /** 워치가 보유한 학습 베이스라인만 계약 키(SNAKE_CASE)로 조립. 미보유 필드는 미전송. */
    fun build(
        restHrMean: Double,
        restHrStd: Double,
        activeHrMean: Double,
        spo2Mean: Double,
        spo2Std: Double,
        bodyTempMean: Double,
        bodyTempStd: Double,
        accelBaselineMean: Float,
        accelBaselineStd: Float,
        alertSpo2Range: Double,
        samplesCount: Int,
    ): Map<String, Any> = mapOf(
        "hr_rest_mean" to restHrMean,
        "hr_rest_std" to restHrStd,
        "hr_active_mean" to activeHrMean,
        "spo2_mean" to spo2Mean,
        "spo2_std" to spo2Std,
        "body_temp_mean" to bodyTempMean,
        "body_temp_std" to bodyTempStd,
        "accel_baseline_mean" to accelBaselineMean,
        "accel_baseline_std" to accelBaselineStd,
        "alert_spo2_range" to alertSpo2Range,
        "samples_count" to samplesCount,
    )
}
