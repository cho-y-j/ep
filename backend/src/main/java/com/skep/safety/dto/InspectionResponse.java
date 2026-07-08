package com.skep.safety.dto;

import com.skep.safety.InspectionKind;
import com.skep.safety.InspectionStatus;
import com.skep.safety.InspectionTarget;
import com.skep.safety.SafetyInspection;

import java.time.LocalDateTime;

public record InspectionResponse(
        Long id,
        Long siteId,
        String siteName,
        Long supplierCompanyId,
        String supplierCompanyName,
        InspectionTarget targetType,
        Long targetId,
        String targetLabel,
        InspectionKind kind,
        LocalDateTime scheduledAt,
        Integer durationMinutes,
        InspectionStatus status,
        LocalDateTime sentAt,
        LocalDateTime confirmedAt,
        LocalDateTime completedAt,
        String resultNotes,
        LocalDateTime createdAt
) {
    public static InspectionResponse from(SafetyInspection s, String siteName, String supplierName, String targetLabel) {
        return new InspectionResponse(
                s.getId(), s.getSiteId(), siteName,
                s.getSupplierCompanyId(), supplierName,
                s.getTargetType(), s.getTargetId(), targetLabel,
                s.getKind(),
                s.getScheduledAt(), s.getDurationMinutes(),
                s.getStatus(),
                s.getSentAt(), s.getConfirmedAt(), s.getCompletedAt(),
                s.getResultNotes(), s.getCreatedAt()
        );
    }
}
