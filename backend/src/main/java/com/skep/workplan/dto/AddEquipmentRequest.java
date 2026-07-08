package com.skep.workplan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddEquipmentRequest(
        @NotNull Long equipmentId,
        @Size(max = 100) String purpose,
        @Size(max = 255) String note,
        Boolean override,
        @Size(max = 255) String overrideReason
) {
}
