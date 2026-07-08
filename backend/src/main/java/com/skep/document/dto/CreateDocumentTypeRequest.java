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
        Integer sortOrder,
        // V14 정책/검증 필드 (선택. 미지정 시 기본값으로)
        Boolean required,
        Boolean blocksAssignment,
        Integer defaultValidMonths,
        Boolean ocrEnabled,
        @Size(max = 64) String ocrExtractType,
        @Size(max = 100) String ocrExpiryFieldKey,
        @Size(max = 64) String verifyEndpoint,
        String requiredFields           // JSON 배열 문자열
) {
}
