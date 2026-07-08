package com.skep.fieldDeployment.dto;

import com.skep.document.OwnerType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** BP 투입 현황 대시보드 한 자원의 모든 정보. */
public record FieldDeploymentBoardItem(
        Long deploymentId,
        OwnerType resourceType,
        Long resourceId,
        String resourceLabel,
        Boolean hasPhoto,
        Long workPlanId,
        Long supplierCompanyId,
        String supplierCompanyName,
        Long targetSiteId,
        String targetSiteName,
        Double siteLatitude,
        Double siteLongitude,
        LocalDate startDate,
        LocalDateTime activatedAt,
        // 작업확인서 통계
        Integer totalDays,
        BigDecimal totalHours,
        LocalDate lastWorkDate,
        Boolean todayAttended,
        Double todayCheckInLat,
        Double todayCheckInLng,
        Double todayCheckOutLat,
        Double todayCheckOutLng,
        List<RecentConfirmation> recentConfirmations
) {
    public record RecentConfirmation(
            Long id,
            LocalDate workDate,
            BigDecimal totalHours,
            String morningTime,
            String afternoonTime,
            Boolean signedBySupplier,
            Boolean signedByBp,
            Long attendancePhotoDocId
    ) {}
}
