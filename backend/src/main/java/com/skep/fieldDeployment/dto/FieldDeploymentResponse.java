package com.skep.fieldDeployment.dto;

import com.skep.document.OwnerType;
import com.skep.fieldDeployment.FieldDeploymentRequest;
import com.skep.fieldDeployment.FieldDeploymentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FieldDeploymentResponse(
        Long id,
        Long supplierCompanyId,
        String supplierCompanyName,
        Long bpCompanyId,
        String bpCompanyName,
        OwnerType resourceType,
        Long resourceId,
        String resourceLabel,
        Long targetSiteId,
        String targetSiteName,
        LocalDate startDate,
        String note,
        FieldDeploymentStatus status,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt,
        String reviewNote,
        LocalDateTime activatedAt,
        LocalDateTime completedAt,
        Long dailyPrice,
        Long monthlyPrice,
        Long otPrice,
        Long nightPrice,
        /** R3 조합 스냅샷 — 같은 값 행을 목록에서 조합 묶음으로 그룹핑(단독 요청=null). */
        Long comboEquipmentId,
        String comboEquipmentLabel
) {
    public static FieldDeploymentResponse from(FieldDeploymentRequest r,
                                                String supplierName, String bpName,
                                                String resourceLabel, String siteName,
                                                String comboEquipmentLabel) {
        return new FieldDeploymentResponse(
                r.getId(), r.getSupplierCompanyId(), supplierName,
                r.getBpCompanyId(), bpName,
                r.getResourceType(), r.getResourceId(), resourceLabel,
                r.getTargetSiteId(), siteName,
                r.getStartDate(), r.getNote(), r.getStatus(),
                r.getRequestedAt(), r.getReviewedAt(), r.getReviewNote(),
                r.getActivatedAt(), r.getCompletedAt(),
                r.getDailyPrice(), r.getMonthlyPrice(), r.getOtPrice(), r.getNightPrice(),
                r.getComboEquipmentId(), comboEquipmentLabel
        );
    }
}
