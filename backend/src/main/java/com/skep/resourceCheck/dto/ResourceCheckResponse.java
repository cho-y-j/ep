package com.skep.resourceCheck.dto;

import com.skep.document.OwnerType;
import com.skep.resourceCheck.ResourceCheckRequest;
import com.skep.resourceCheck.ResourceCheckStatus;
import com.skep.resourceCheck.ResourceCheckType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ResourceCheckResponse(
        Long id,
        Long workPlanId,
        OwnerType ownerType,
        Long ownerId,
        String ownerLabel,
        Long supplierCompanyId,
        String supplierCompanyName,
        Long bpCompanyId,
        ResourceCheckType checkType,
        LocalDate dueDate,
        String notes,
        ResourceCheckStatus status,
        Long documentId,
        LocalDateTime issuedAt,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        String reviewNote
) {
    public static ResourceCheckResponse from(ResourceCheckRequest r, String ownerLabel, String supplierName) {
        return new ResourceCheckResponse(
                r.getId(), r.getWorkPlanId(),
                r.getOwnerType(), r.getOwnerId(), ownerLabel,
                r.getSupplierCompanyId(), supplierName,
                r.getBpCompanyId(),
                r.getCheckType(), r.getDueDate(), r.getNotes(),
                r.getStatus(), r.getDocumentId(),
                r.getIssuedAt(), r.getSubmittedAt(), r.getReviewedAt(), r.getReviewNote()
        );
    }
}
