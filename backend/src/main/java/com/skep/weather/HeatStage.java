package com.skep.weather;

/**
 * 체감온도 기반 폭염 단계 + 법정 휴식 정책.
 * 근거: 산업안전보건기준에 관한 규칙(폭염), 근로기준법 제54조(휴게).
 *
 * - NONE   : 폭염 아님 → 근로기준법 4시간 연속작업 시 휴게
 * - CAUTION: 체감 31℃↑ 주의 → 2시간마다 휴식 등 조치
 * - WARNING: 체감 33℃↑ 경고(의무) → 2시간마다 20분 이상 휴식
 * - DANGER : 체감 35℃↑ 위험(권고) → 매시간 15분 + 14~17시 옥외작업 중지
 * - EXTREME: 체감 38℃↑ 심각(권고) → 매시간 15분 + 긴급작업 외 중지
 */
public enum HeatStage {
    NONE(Double.NEGATIVE_INFINITY, "info", 240),
    CAUTION(31.0, "caution", 120),
    WARNING(33.0, "warning", 120),
    DANGER(35.0, "warning", 60),
    EXTREME(38.0, "warning", 60);

    private final double minFeelsLike;
    private final String level;
    private final int intervalMinutes;

    HeatStage(double minFeelsLike, String level, int intervalMinutes) {
        this.minFeelsLike = minFeelsLike;
        this.level = level;
        this.intervalMinutes = intervalMinutes;
    }

    /** 알림 레벨 (대시보드 색상). danger 는 SafetyAlertBroadcaster 의 전체 FCM 트리거 — 폭염은 의도적으로 미사용. */
    public String level() { return level; }

    /** 다음 휴식 알림까지 최소 간격(분). */
    public int intervalMinutes() { return intervalMinutes; }

    public static HeatStage of(double feelsLike) {
        if (feelsLike >= EXTREME.minFeelsLike) return EXTREME;
        if (feelsLike >= DANGER.minFeelsLike) return DANGER;
        if (feelsLike >= WARNING.minFeelsLike) return WARNING;
        if (feelsLike >= CAUTION.minFeelsLike) return CAUTION;
        return NONE;
    }

    public String label() {
        return switch (this) {
            case NONE -> "정상";
            case CAUTION -> "폭염 주의";
            case WARNING -> "폭염 경고";
            case DANGER -> "폭염 위험";
            case EXTREME -> "폭염 심각";
        };
    }

    /**
     * 워치로 보낼 휴식 알림 메시지.
     * @param feelsLike 체감온도(℃). null 이면 기온 미상 → 근로기준법 휴게 안내만.
     * @param midday    현재 14~17시 여부 (옥외작업 중지 권고 부기).
     */
    public String restMessage(Double feelsLike, boolean midday) {
        String t = feelsLike != null ? String.format("체감 %.0f℃ — ", feelsLike) : "";
        String stop = midday ? " (오후 2~5시 옥외작업 중지 권고)" : "";
        return switch (this) {
            case NONE -> "4시간 연속 작업 — 30분 이상 휴식하세요 (근로기준법)";
            case CAUTION -> "폭염 주의 " + t + "물·그늘에서 휴식하세요";
            case WARNING -> "폭염 경고 " + t + "2시간마다 20분 이상 휴식하세요 (법정 의무)";
            case DANGER -> "폭염 위험 " + t + "매시간 15분 이상 휴식하세요" + stop;
            case EXTREME -> "폭염 심각 " + t + "매시간 15분 휴식, 긴급작업 외 중지" + stop;
        };
    }
}
