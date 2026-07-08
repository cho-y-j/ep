package com.skep.safety.dto;

import com.skep.safety.InspectionKind;
import com.skep.safety.InspectionTarget;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateInspectionRequest(
        @NotNull Long siteId,
        @NotNull InspectionTarget targetType,
        @NotNull Long targetId,
        @NotNull InspectionKind kind,
        @NotNull LocalDateTime scheduledAt,
        Integer durationMinutes,
        Long supplierCompanyId,
        Long inspectorId
) {
}
