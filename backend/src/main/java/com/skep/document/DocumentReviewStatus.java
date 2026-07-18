package com.skep.document;

/**
 * 서류 심사 봉투(DocumentReview) 상태.
 *
 * - PENDING: 발송 직후 기본 상태(심사중). BP 검토 대기.
 * - APPROVED: BP 가 서류를 확인하고 승인함.
 * - REJECTED: BP 가 반려함. rejected_reason 함께 기록.
 */
public enum DocumentReviewStatus {
    PENDING,
    APPROVED,
    REJECTED
}
