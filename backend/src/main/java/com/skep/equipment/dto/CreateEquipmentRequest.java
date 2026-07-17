package com.skep.equipment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEquipmentRequest(
        Long supplierId,
        @Size(max = 32) String vehicleNo,
        @NotNull @Size(max = 32) String category,
        @Size(max = 100) String model,
        @Size(max = 100) String manufacturer,
        @Min(1900) Integer year,
        // 외부 조달 장비 — 우리 공급사 장비가 아니면 true. 외부면 소유자(사업자) 별도.
        Boolean isExternal,
        @Size(max = 100) String vehicleOwnerName,
        @Size(max = 32) String vehicleOwnerBusinessNo,
        // 기사(조종원) — 등록된 인력(Person) 선택. 있으면 장비에 연결.
        Long operatorPersonId,
        // 검사만료일(정기검사 유효기간, YYYY-MM-DD) — 폼 입력 또는 자동차등록증 OCR 자동채움. inspection_due_date 로 저장(만료관리).
        @Size(max = 10) String inspectionDueDate
) {
}
