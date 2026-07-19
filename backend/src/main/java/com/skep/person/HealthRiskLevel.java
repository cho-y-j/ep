package com.skep.person;

/**
 * P5-W4 2겹 — 뇌심혈관 건강 위험군 태깅(건강검진 서류 기반 수동 태깅).
 * HIGH = 워치 정책 YELLOW 상향(WatchPolicyService) + 혈압 체크인 필수 대상(오늘 미측정 목록에 노출).
 */
public enum HealthRiskLevel {
    NORMAL, CAUTION, HIGH;

    /** 저장 문자열/NULL(레거시)을 등급으로 — 미상/NULL 은 NORMAL. */
    public static HealthRiskLevel of(String s) {
        if (s == null) return NORMAL;
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
