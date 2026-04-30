package com.skep.document.dto;

import com.skep.document.OwnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDocumentTypeRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull OwnerType appliesTo,
        boolean hasExpiry,
        boolean requiresVerification,
        Integer sortOrder
) {
}
