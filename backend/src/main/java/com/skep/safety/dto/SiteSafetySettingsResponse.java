package com.skep.safety.dto;

import com.skep.safety.SafetyThresholds;
import com.skep.safety.SiteSafetySettings;

/**
 * 현장 안전설정 응답 — 현재 유효값 + 법정 기준(완화 금지 배지용).
 * configured=false 면 저장 행이 없어 법정 기본값을 그대로 표시 중.
 */
public record SiteSafetySettingsResponse(
        Long siteId,
        boolean configured,
        double tempCaution,
        double tempWarning,
        double tempDanger,
        double tempExtreme,
        int restIntervalMin,
        int restDurationMin,
        int middayStartHour,
        int middayEndHour,
        double windStopMps,
        boolean enforceDailyInspectionGate,
        Integer maintenanceIntervalHours,
        // 법정 기준(현장 설정은 이보다 완화 불가).
        double legalTempCaution,
        double legalTempWarning,
        double legalTempDanger,
        double legalTempExtreme,
        int legalRestInterval,
        int legalRestDuration,
        double legalWindStop,
        int defaultMaintenanceHours
) {
    /** 저장 행이 없는 현장 — 법정 기본값 표시. */
    public static SiteSafetySettingsResponse ofDefaults(Long siteId) {
        return build(siteId, false, SafetyThresholds.legalDefault());
    }

    public static SiteSafetySettingsResponse of(SiteSafetySettings s) {
        return build(s.getSiteId(), true, SafetyThresholds.from(s));
    }

    private static SiteSafetySettingsResponse build(Long siteId, boolean configured, SafetyThresholds t) {
        return new SiteSafetySettingsResponse(
                siteId, configured,
                t.tempCaution(), t.tempWarning(), t.tempDanger(), t.tempExtreme(),
                t.restIntervalMin(), t.restDurationMin(), t.middayStartHour(), t.middayEndHour(),
                t.windStopMps(), t.enforceDailyInspectionGate(), t.maintenanceIntervalHours(),
                SafetyThresholds.LEGAL_CAUTION, SafetyThresholds.LEGAL_WARNING,
                SafetyThresholds.LEGAL_DANGER, SafetyThresholds.LEGAL_EXTREME,
                SafetyThresholds.LEGAL_REST_INTERVAL, SafetyThresholds.LEGAL_REST_DURATION,
                SafetyThresholds.LEGAL_WIND_STOP, SafetyThresholds.DEFAULT_MAINTENANCE_HOURS);
    }
}
