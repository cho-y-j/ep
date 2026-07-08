package com.skep.equipment.dto;

import com.skep.equipment.EquipmentCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateEquipmentRequest(
        @Size(max = 32) String vehicleNo,
        EquipmentCategory category,
        @Size(max = 100) String model,
        @Size(max = 100) String manufacturer,
        @Min(1900) Integer year,
        Boolean isExternal,
        @Size(max = 100) String vehicleOwnerName,
        @Size(max = 32) String vehicleOwnerBusinessNo
) {
}
