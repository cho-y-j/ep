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
        String reviewNote,
        /** R2 조합 스냅샷 — 같은 값 행을 목록에서 조합 묶음으로 그룹핑(단독 발행=null). */
        Long comboEquipmentId,
        String comboEquipmentLabel
) {
    public static ResourceCheckResponse from(ResourceCheckRequest r, String ownerLabel, String supplierName,
                                             String comboEquipmentLabel) {
        return new ResourceCheckResponse(
                r.getId(), r.getWorkPlanId(),
                r.getOwnerType(), r.getOwnerId(), ownerLabel,
                r.getSupplierCompanyId(), supplierName,
                r.getBpCompanyId(),
                r.getCheckType(), r.getDueDate(), r.getNotes(),
                r.getStatus(), r.getDocumentId(),
                r.getIssuedAt(), r.getSubmittedAt(), r.getReviewedAt(), r.getReviewNote(),
                r.getComboEquipmentId(), comboEquipmentLabel
        );
    }
}
