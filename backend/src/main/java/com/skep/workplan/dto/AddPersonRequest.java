package com.skep.workplan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddPersonRequest(
        @NotNull Long personId,
        Long equipmentId,                  // null = 현장 전체 인원 (안전관리자 등)
        @Size(max = 32) String role,
        @Size(max = 255) String note,
        Boolean override,
        @Size(max = 255) String overrideReason
) {
}
