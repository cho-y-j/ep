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
        @Min(1900) Integer year,
        // 외부 조달 장비 — 우리 공급사 장비가 아니면 true. 외부면 소유자(사업자) 별도.
        Boolean isExternal,
        @Size(max = 100) String vehicleOwnerName,
        @Size(max = 32) String vehicleOwnerBusinessNo,
        // 기사(조종원) — 등록된 인력(Person) 선택. 있으면 장비에 연결.
        Long operatorPersonId
) {
}
