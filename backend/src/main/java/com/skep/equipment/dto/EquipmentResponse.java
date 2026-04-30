package com.skep.equipment.dto;

import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentCategory;

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
        LocalDateTime createdAt
) {
    public static EquipmentResponse from(Equipment e) {
        return from(e, 0L);
    }

    public static EquipmentResponse from(Equipment e, long expiringCount) {
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
                e.getCreatedAt()
        );
    }
}
