package com.skep.safety.dto;

/**
 * 현장 안전설정 저장 요청. null 필드는 법정 기본값으로 대체(부분 저장 안전).
 * 값 유효성·법정 완화 금지 가드는 SafetyThresholds.validateNotWeakerThanLegal 에서.
 */
public record SiteSafetySettingsRequest(
        Double tempCaution,
        Double tempWarning,
        Double tempDanger,
        Double tempExtreme,
        Integer restIntervalMin,
        Integer restDurationMin,
        Integer middayStartHour,
        Integer middayEndHour,
        Double windStopMps,
        Boolean enforceDailyInspectionGate,
        Integer maintenanceIntervalHours
) {
}
