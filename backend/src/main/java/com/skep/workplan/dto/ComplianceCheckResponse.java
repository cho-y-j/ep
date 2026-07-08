package com.skep.workplan.dto;

import com.skep.document.OwnerType;
import com.skep.workplan.ComplianceStatus;
import com.skep.workplan.WorkPlanComplianceCheck;

import java.time.LocalDateTime;

public record ComplianceCheckResponse(
        Long id,
        OwnerType targetType,
        Long targetId,
        ComplianceStatus status,
        String reason,
        LocalDateTime checkedAt,
        Long overrideBy,
        String overrideReason
) {
    public static ComplianceCheckResponse from(WorkPlanComplianceCheck c) {
        return new ComplianceCheckResponse(
                c.getId(), c.getTargetType(), c.getTargetId(),
                c.getStatus(), c.getReason(), c.getCheckedAt(),
                c.getOverrideBy(), c.getOverrideReason()
        );
    }
}
