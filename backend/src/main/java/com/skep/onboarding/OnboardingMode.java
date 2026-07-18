package com.skep.onboarding;

public enum OnboardingMode {
    REQUESTED,   // 공급사 신고 → BP 소급 승인 대기
    APPROVED,    // BP 소급 일괄 승인 완료
    VERBAL       // 공급사 구두승인 기록 — 즉시 확정(BP 미사용)
}
