package com.skep.quotation;

/** 견적 요청 lifecycle. */
public enum QuotationStatus {
    DRAFT,      // (현재 미사용 — UI 에서 임시저장 도입 시)
    SENT,       // 발송됨, 응답 대기
    CLOSED,     // 모든 target 처리 완료 또는 BP/ADMIN 수동 종료
    CANCELLED   // BP/ADMIN 가 취소
}
