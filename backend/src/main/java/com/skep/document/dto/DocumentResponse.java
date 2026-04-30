package com.skep.document.dto;

import com.skep.document.Document;
import com.skep.document.OwnerType;

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
                d.getCreatedAt()
        );
    }

    /** 호환용: type 이름만 전달, has_expiry는 false 가정 (사용 안 권장) */
    public static DocumentResponse from(Document d, String typeName) {
        return from(d, typeName, false);
    }
}
