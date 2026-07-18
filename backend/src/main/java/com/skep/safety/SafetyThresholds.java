package com.skep.safety;

import com.skep.common.ApiException;
import com.skep.weather.HeatStage;

/**
 * 현장 안전설정의 유효 임계값 — 설정 행이 없으면 법정 기본값(HeatStage 하드코딩과 동일).
 * HeatStage.of()/휴식 간격/무더위 시간대를 현장 오버라이드로 감싸되, 법정보다 완화 불가(가드).
 *
 * 방향: 온도 임계 낮추기·휴식 간격 줄이기·휴식 시간 늘리기·풍속 임계 낮추기 = 강화(허용).
 *       그 반대 = 완화(금지, validateNotWeakerThanLegal 에서 400).
 */
public record SafetyThresholds(
        double tempCaution, double tempWarning, double tempDanger, double tempExtreme,
        int restIntervalMin, int restDurationMin,
        int middayStartHour, int middayEndHour,
        double windStopMps, boolean enforceDailyInspectionGate, Integer maintenanceIntervalHours) {

    // 법정 기본값(= HeatStage 하드코딩, 산업안전보건기준 규칙·근로기준법). 완화 금지 기준선.
    public static final double LEGAL_CAUTION = 31.0;
    public static final double LEGAL_WARNING = 33.0;
    public static final double LEGAL_DANGER = 35.0;
    public static final double LEGAL_EXTREME = 38.0;
    public static final int LEGAL_REST_INTERVAL = 120;
    public static final int LEGAL_REST_DURATION = 20;
    public static final double LEGAL_WIND_STOP = 10.0;
    public static final int DEFAULT_MIDDAY_START = 14;
    public static final int DEFAULT_MIDDAY_END = 17;
    public static final int DEFAULT_MAINTENANCE_HOURS = 250;

    /** 설정 없는 현장 — 법정 기본값(HeatStage 그대로, 무회귀). */
    public static SafetyThresholds legalDefault() {
        return new SafetyThresholds(LEGAL_CAUTION, LEGAL_WARNING, LEGAL_DANGER, LEGAL_EXTREME,
                LEGAL_REST_INTERVAL, LEGAL_REST_DURATION, DEFAULT_MIDDAY_START, DEFAULT_MIDDAY_END,
                LEGAL_WIND_STOP, false, DEFAULT_MAINTENANCE_HOURS);
    }

    public static SafetyThresholds from(SiteSafetySettings s) {
        if (s == null) return legalDefault();
        return new SafetyThresholds(s.getTempCaution(), s.getTempWarning(), s.getTempDanger(), s.getTempExtreme(),
                s.getRestIntervalMin(), s.getRestDurationMin(), s.getMiddayStartHour(), s.getMiddayEndHour(),
                s.getWindStopMps(), s.isEnforceDailyInspectionGate(), s.getMaintenanceIntervalHours());
    }

    /** 폭염 단계 — 오버라이드된 임계온도 기준(HeatStage.of 의 현장 버전). */
    public HeatStage stageOf(double feelsLike) {
        if (feelsLike >= tempExtreme) return HeatStage.EXTREME;
        if (feelsLike >= tempDanger) return HeatStage.DANGER;
        if (feelsLike >= tempWarning) return HeatStage.WARNING;
        if (feelsLike >= tempCaution) return HeatStage.CAUTION;
        return HeatStage.NONE;
    }

    /**
     * 휴식 간격(분). 폭염 단계는 min(법정 단계 간격, 설정 간격) 로 강화만 반영(약화 없음).
     * NONE(비폭염)은 근로기준법 4시간 규칙(HeatStage.NONE=240) 고정 — 폭염 설정 범위 밖.
     */
    public int intervalMinutes(HeatStage stage) {
        if (stage == HeatStage.NONE) return stage.intervalMinutes();
        return Math.min(stage.intervalMinutes(), restIntervalMin);
    }

    /** 무더위 시간대(옥외작업 중지 권고) 여부. */
    public boolean isMidday(int hour) {
        return hour >= middayStartHour && hour < middayEndHour;
    }

    /** 법정 완화 금지 가드 — 위반 시 400. 저장 전 호출. */
    public void validateNotWeakerThanLegal() {
        requireTempAtMost("주의", tempCaution, LEGAL_CAUTION);
        requireTempAtMost("경고(휴식)", tempWarning, LEGAL_WARNING);
        requireTempAtMost("위험", tempDanger, LEGAL_DANGER);
        requireTempAtMost("중지", tempExtreme, LEGAL_EXTREME);
        if (restIntervalMin > LEGAL_REST_INTERVAL) {
            throw ApiException.badRequest("SAFETY_WEAKER_THAN_LEGAL",
                    "휴식 간격은 법정 기준 " + LEGAL_REST_INTERVAL + "분보다 길게 설정할 수 없습니다. 더 짧게(자주 휴식)만 가능합니다.");
        }
        if (restDurationMin < LEGAL_REST_DURATION) {
            throw ApiException.badRequest("SAFETY_WEAKER_THAN_LEGAL",
                    "휴식 시간은 법정 기준 " + LEGAL_REST_DURATION + "분보다 짧게 설정할 수 없습니다. 더 길게만 가능합니다.");
        }
        if (windStopMps > LEGAL_WIND_STOP) {
            throw ApiException.badRequest("SAFETY_WEAKER_THAN_LEGAL",
                    "풍속 작업중지 임계는 법정 기준 " + fmt(LEGAL_WIND_STOP) + "m/s보다 높게 설정할 수 없습니다. 더 낮게(빨리 중지)만 가능합니다.");
        }
        if (restIntervalMin < 1 || restDurationMin < 1) {
            throw ApiException.badRequest("SAFETY_INVALID", "휴식 간격·시간은 1분 이상이어야 합니다.");
        }
        if (middayStartHour < 0 || middayEndHour > 24 || middayStartHour >= middayEndHour) {
            throw ApiException.badRequest("SAFETY_INVALID", "무더위 시간대가 올바르지 않습니다(0~24, 시작 < 끝).");
        }
        if (maintenanceIntervalHours != null && maintenanceIntervalHours < 1) {
            throw ApiException.badRequest("SAFETY_INVALID", "정비 주기는 1시간 이상이거나 비워두세요(비활성).");
        }
    }

    private static void requireTempAtMost(String label, double val, double legal) {
        if (val > legal) {
            throw ApiException.badRequest("SAFETY_WEAKER_THAN_LEGAL",
                    label + " 임계온도는 법정 기준 " + fmt(legal) + "℃보다 높게 설정할 수 없습니다. 더 낮게(강화)만 가능합니다.");
        }
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
