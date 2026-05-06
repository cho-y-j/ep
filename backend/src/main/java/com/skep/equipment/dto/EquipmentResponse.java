package com.skep.equipment.dto;

import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EquipmentResponse(
        Long id,
        Long supplierId,
        String vehicleNo,
        EquipmentCategory category,
        String model,
        String manufacturer,
        Integer year,
        boolean hasPhoto,
        long expiringCount,
        // 신규 필드
        String code,
        String serialNumber,
        Integer usageHours,
        Integer weightKg,
        BigDecimal bucketCapacity,
        LocalDate insuranceExpiry,
        int operatingHours,
        int idleHours,
        int downtimeHours,
        Integer utilizationPct,        // operating / (operating + idle + downtime)
        // ---
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EquipmentResponse from(Equipment e) {
        return from(e, 0L);
    }

    public static EquipmentResponse from(Equipment e, long expiringCount) {
        int op = e.getOperatingHours();
        int idle = e.getIdleHours();
        int down = e.getDowntimeHours();
        int total = op + idle + down;
        Integer utilization = total > 0 ? Math.round((op * 100f) / total) : null;

        return new EquipmentResponse(
                e.getId(),
                e.getSupplierId(),
                e.getVehicleNo(),
                e.getCategory(),
                e.getModel(),
                e.getManufacturer(),
                e.getYear(),
                e.getPhotoKey() != null,
                expiringCount,
                e.getCode(),
                e.getSerialNumber(),
                e.getUsageHours(),
                e.getWeightKg(),
                e.getBucketCapacity(),
                e.getInsuranceExpiry(),
                op,
                idle,
                down,
                utilization,
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
