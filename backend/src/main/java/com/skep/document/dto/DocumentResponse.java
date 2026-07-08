package com.skep.document.dto;

import com.skep.document.Document;
import com.skep.document.OwnerType;
import com.skep.document.PiiMasker;
import com.skep.document.VerificationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        Long documentTypeId,
        String documentTypeName,
        boolean documentTypeHasExpiry,
        OwnerType ownerType,
        Long ownerId,
        String fileName,
        long fileSize,
        String contentType,
        LocalDate expiryDate,
        boolean verified,
        // V14 검증 필드
        VerificationStatus verificationStatus,
        Long verifiedBy,
        LocalDateTime verifiedAt,
        String rejectedReason,
        Long previousDocumentId,
        String verificationResult,    // JSON 문자열
        String extractedData,         // JSON 문자열
        // ---
        LocalDateTime createdAt
) {
    public static DocumentResponse from(Document d, String typeName, boolean hasExpiry) {
        return new DocumentResponse(
                d.getId(),
                d.getDocumentTypeId(),
                typeName,
                hasExpiry,
                d.getOwnerType(),
                d.getOwnerId(),
                d.getFileName(),
                d.getFileSize(),
                d.getContentType(),
                d.getExpiryDate(),
                d.isVerified(),
                d.getVerificationStatus(),
                d.getVerifiedBy(),
                d.getVerifiedAt(),
                d.getRejectedReason(),
                d.getPreviousDocumentId(),
                PiiMasker.mask(d.getVerificationResult()),
                PiiMasker.mask(d.getExtractedData()),
                d.getCreatedAt()
        );
    }

    /** 호환용: type 이름만 전달, has_expiry는 false 가정 (사용 안 권장) */
    public static DocumentResponse from(Document d, String typeName) {
        return from(d, typeName, false);
    }
}
