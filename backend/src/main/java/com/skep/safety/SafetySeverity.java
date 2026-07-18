package com.skep.safety;

/**
 * S5' 안전알림 3등급.
 * - EMERGENCY : 작업중지(강풍·폭염 38℃)·워치 응급/낙상 → 항상 확인응답(ack) 대상.
 * - CAUTION   : 휴식·수분(폭염 31/33/35 단계) → 확인응답 대상.
 * - NORMAL    : 공지·만료 등 일반 → 확인응답 불요.
 */
public enum SafetySeverity {
    EMERGENCY, CAUTION, NORMAL;

    /** EMERGENCY/CAUTION 은 [확인] 응답을 받아야 한다(미확인 시 에스컬레이션). */
    public boolean ackRequired() {
        return this != NORMAL;
    }

    /** 저장된 문자열(또는 NULL=레거시)을 등급으로. 미상/NULL 은 NORMAL. */
    public static SafetySeverity of(String s) {
        if (s == null) return NORMAL;
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
