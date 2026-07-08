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
        Long nightPrice
) {
    public static FieldDeploymentResponse from(FieldDeploymentRequest r,
                                                String supplierName, String bpName,
                                                String resourceLabel, String siteName) {
        return new FieldDeploymentResponse(
                r.getId(), r.getSupplierCompanyId(), supplierName,
                r.getBpCompanyId(), bpName,
                r.getResourceType(), r.getResourceId(), resourceLabel,
                r.getTargetSiteId(), siteName,
                r.getStartDate(), r.getNote(), r.getStatus(),
                r.getRequestedAt(), r.getReviewedAt(), r.getReviewNote(),
                r.getActivatedAt(), r.getCompletedAt(),
                r.getDailyPrice(), r.getMonthlyPrice(), r.getOtPrice(), r.getNightPrice()
        );
    }
}
