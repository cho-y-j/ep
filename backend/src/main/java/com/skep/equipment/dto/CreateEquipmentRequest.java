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
        // Phase4: 외부 장비 기사(조종원) 등록 + 로그인 계정 (선택) — 이름/아이디/비번 다 채우면 함께 생성·연결.
        @Size(max = 100) String operatorName,
        @Size(max = 32) String operatorPhone,
        @Size(max = 64) String operatorUsername,
        @Size(max = 100) String operatorPassword
) {
}
