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
        LocalDateTime createdAt
) {
    public static EquipmentResponse from(Equipment e) {
        return new EquipmentResponse(
                e.getId(),
                e.getSupplierId(),
                e.getVehicleNo(),
                e.getCategory(),
                e.getModel(),
                e.getManufacturer(),
                e.getYear(),
                e.getPhotoKey() != null,
                e.getCreatedAt()
        );
    }
}
