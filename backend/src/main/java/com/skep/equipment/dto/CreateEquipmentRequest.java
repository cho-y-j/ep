package com.skep.equipment.dto;

import com.skep.equipment.EquipmentCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEquipmentRequest(
        Long supplierId,
        @Size(max = 32) String vehicleNo,
        @NotNull EquipmentCategory category,
        @Size(max = 100) String model,
        @Size(max = 100) String manufacturer,
        @Min(1900) Integer year
) {
}
