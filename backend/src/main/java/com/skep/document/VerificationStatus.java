package com.skep.document;

/**
 * 서류 검증 상태. 기존 boolean verified 와 별개로 더 세밀한 상태 추적용.
 *
 * - PENDING: 업로드 직후 기본 상태. ADMIN 검증 대기.
 * - VERIFIED: ADMIN 이 검증 표시함.
 * - REJECTED: ADMIN 이 반려함. rejected_reason 함께 기록.
 * - OCR_REVIEW_REQUIRED: OCR 결과가 모호하여 사람 검토 필요. Phase F(OCR) 에서 의미.
 */
public enum VerificationStatus {
    PENDING,
    VERIFIED,
    REJECTED,
    OCR_REVIEW_REQUIRED
}
