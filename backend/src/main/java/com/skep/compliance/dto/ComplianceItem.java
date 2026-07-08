package com.skep.compliance.dto;

/** 자원 1건의 단일 서류 종류 컴플라이언스 한 줄. */
public record ComplianceItem(
        Long documentTypeId,
        String documentTypeName,
        boolean required,
        boolean blocksAssignment,
        boolean hasExpiry,
        // 현재 자원 상태
        boolean present,           // chain head 가 존재하는가
        boolean verified,          // chain head 가 VERIFIED 인가
        boolean rejected,          // chain head 가 REJECTED 인가
        boolean ocrReviewRequired, // chain head 가 OCR_REVIEW_REQUIRED 인가
        boolean expired,
        boolean expiringSoon,      // ≤30일
        Long documentId,
        String expiryDate,
        // 진행 중 보완 요청
        boolean openSupplement
) {}
