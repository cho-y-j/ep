package com.skep.resourceCheck;

public enum ResourceCheckStatus {
    REQUESTED,   // BP 발송 후 공급사 회신 대기
    SUBMITTED,   // 공급사가 서류 첨부 회신 — BP 검토 대기
    APPROVED,    // BP 승인 → 자원 "투입 대기"
    REJECTED,    // BP 반려 → 공급사 재제출 필요
    CANCELLED    // BP가 취소
}
