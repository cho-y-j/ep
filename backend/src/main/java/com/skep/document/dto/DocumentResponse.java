package com.skep.document.dto;

import com.skep.document.Document;
import com.skep.document.OwnerType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        Long documentTypeId,
        String documentTypeName,
        OwnerType ownerType,
        Long ownerId,
        String fileName,
        long fileSize,
        String contentType,
        LocalDate expiryDate,
        boolean verified,
        LocalDateTime createdAt
) {
    public static DocumentResponse from(Document d, String typeName) {
        return new DocumentResponse(
                d.getId(),
                d.getDocumentTypeId(),
                typeName,
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
}
