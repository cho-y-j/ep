package com.skep.quotation;

/**
 * 견적 target (supplier×equipment) lifecycle.
 *
 *   PENDING        — 발송 후 공급사 응답 대기
 *   ACCEPTED       — 공급사가 BP 제안 가격 수락
 *   REJECTED       — 공급사가 거부
 *   FINAL_ACCEPTED — ACCEPTED 상태에서 BP/ADMIN 가 최종 채택 (→ WorkPlan 자원으로 반영)
 *   EXPIRED        — (현재 미사용 — 만료 cron 도입 시)
 */
public enum QuotationTargetStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    FINAL_ACCEPTED,
    EXPIRED
}
