package com.skep.document.dto;

import com.skep.document.DocumentType;
import com.skep.document.OwnerType;

public record DocumentTypeResponse(
        Long id,
        String name,
        OwnerType appliesTo,
        boolean hasExpiry,
        boolean requiresVerification,
        int sortOrder,
        boolean active
) {
    public static DocumentTypeResponse from(DocumentType t) {
        return new DocumentTypeResponse(
                t.getId(), t.getName(), t.getAppliesTo(),
                t.isHasExpiry(), t.isRequiresVerification(),
                t.getSortOrder(), t.isActive()
        );
    }
}
