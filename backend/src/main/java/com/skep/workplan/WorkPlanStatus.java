package com.skep.workplan;

public enum WorkPlanStatus {
    DRAFT,         // 초안
    SUBMITTED,     // 제출됨 (BP 가 ADMIN 검토 요청)
    APPROVED,      // 승인됨
    IN_PROGRESS,   // 진행 중 (당일 작업 시작)
    DONE,          // 완료
    CANCELLED      // 취소
}
