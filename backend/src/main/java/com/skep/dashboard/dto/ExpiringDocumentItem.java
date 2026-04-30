package com.skep.dashboard.dto;

import com.skep.document.OwnerType;

import java.time.LocalDate;

public record ExpiringDocumentItem(
        Long id,
        Long documentTypeId,
        String documentTypeName,
        OwnerType ownerType,
        Long ownerId,
        String ownerName,
        LocalDate expiryDate,
        long daysLeft
) {
}
