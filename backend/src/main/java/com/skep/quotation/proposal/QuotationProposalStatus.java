package com.skep.quotation.proposal;

/**
 * 공급사 제안 상태.
 *   SUBMITTED       — 제출 직후, BP 검토 대기
 *   PENDING_REVIEW  — BP 가 견적 spec 수정 → 공급사 재확인 필요
 *   FINAL_ACCEPTED  — BP 최종 선정. 작업계획서 자동 생성
 *   REJECTED        — BP 가 거절했거나, 다른 제안이 선정돼 자동 거절
 *   WITHDRAWN       — 공급사 본인 철회
 */
public enum QuotationProposalStatus {
    SUBMITTED, PENDING_REVIEW, FINAL_ACCEPTED, REJECTED, WITHDRAWN
}
