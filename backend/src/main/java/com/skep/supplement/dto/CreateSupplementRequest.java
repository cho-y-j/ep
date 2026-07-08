package com.skep.supplement.dto;

import com.skep.document.OwnerType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSupplementRequest(
        @NotNull OwnerType targetOwnerType,
        @NotNull Long targetOwnerId,
        @NotNull Long documentTypeId,
        Long contextSiteId,
        Long contextWorkPlanId,
        @Size(max = 2000) String reason
) {}
