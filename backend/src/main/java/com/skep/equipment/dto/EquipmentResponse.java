package com.skep.equipment.dto;

import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentAssignmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EquipmentResponse(
        Long id,
        Long supplierId,
        String supplierName,
        String vehicleNo,
        String category,
        String model,
        String manufacturer,
        Integer year,
        boolean isExternal,
        String vehicleOwnerName,
        String vehicleOwnerBusinessNo,
        boolean hasPhoto,
        long expiringCount,
        // V8 신규 필드
        String code,
        String serialNumber,
        Integer usageHours,
        Integer weightKg,
        BigDecimal bucketCapacity,
        LocalDate insuranceExpiry,
        LocalDate inspectionDueDate,
        LocalDate oilChangeDueDate,
        LocalDate registrationExpiry,
        int operatingHours,
        int idleHours,
        int downtimeHours,
        Integer utilizationPct,        // operating / (operating + idle + downtime)
        // V11 배치 정보
        Long currentSiteId,
        String currentSiteName,
        EquipmentAssignmentStatus assignmentStatus,
        LocalDateTime lastAssignedAt,
        // ---
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        // Phase4: 외부 장비 기사(조종원) Person 연결
        Long operatorPersonId
) {
    public static EquipmentResponse from(Equipment e) {
        return from(e, 0L, null, null);
    }

    public static EquipmentResponse from(Equipment e, long expiringCount) {
        return from(e, expiringCount, null, null);
    }

    public static EquipmentResponse from(Equipment e, long expiringCount, String currentSiteName) {
        return from(e, expiringCount, currentSiteName, null);
    }

    public static EquipmentResponse from(Equipment e, long expiringCount, String currentSiteName, String supplierName) {
        int op = e.getOperatingHours();
        int idle = e.getIdleHours();
        int down = e.getDowntimeHours();
        int total = op + idle + down;
        Integer utilization = total > 0 ? Math.round((op * 100f) / total) : null;

        return new EquipmentResponse(
                e.getId(),
                e.getSupplierId(),
                supplierName,
                e.getVehicleNo(),
                e.getCategory(),
                e.getModel(),
                e.getManufacturer(),
                e.getYear(),
                e.isExternal(),
                e.getVehicleOwnerName(),
                e.getVehicleOwnerBusinessNo(),
                e.getPhotoKey() != null,
                expiringCount,
                e.getCode(),
                e.getSerialNumber(),
                e.getUsageHours(),
                e.getWeightKg(),
                e.getBucketCapacity(),
                e.getInsuranceExpiry(),
                e.getInspectionDueDate(),
                e.getOilChangeDueDate(),
                e.getRegistrationExpiry(),
                op,
                idle,
                down,
                utilization,
                e.getCurrentSiteId(),
                currentSiteName,
                e.getAssignmentStatus(),
                e.getLastAssignedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getOperatorPersonId()
        );
    }
}
