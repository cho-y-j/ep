package com.skep.document.dto;

import com.skep.document.OwnerType;
import com.skep.document.VerificationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** OCR 검토 큐 1행. 문서 + owner/supplier 메타까지 합본. */
public record ReviewItemResponse(
        Long id,
        Long documentTypeId,
        String documentTypeName,
        OwnerType ownerType,
        Long ownerId,
        String ownerName,
        String ownerSubLabel,
        String ownerAssignmentStatus,
        boolean ownerExternal,
        String ownerBusinessName,
        Long ownerSupplierId,
        String ownerSupplierName,
        String fileName,
        LocalDate expiryDate,
        VerificationStatus verificationStatus,
        String rejectedReason,
        String verificationResult,
        String extractedData,
        LocalDateTime createdAt
) {
}
