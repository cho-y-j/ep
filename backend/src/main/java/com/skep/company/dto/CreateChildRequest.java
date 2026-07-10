package com.skep.company.dto;

import com.skep.company.CompanyType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * V77: 부모 장비공급사가 하위(자식) 공급사를 등록할 때의 입력.
 * - type: EQUIPMENT 또는 MANPOWER.
 * - parentCompanyId: ADMIN 이 대행 등록 시에만 사용. EQUIPMENT_SUPPLIER 는 본인이 부모(무시).
 * - admin* : 함께 만들 자식 회사 관리자(master) 계정. 셋 다 있으면 생성, 없으면 스킵.
 */
public record CreateChildRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 32) String businessNumber,
        @NotNull CompanyType type,
        Long parentCompanyId,
        @Email String adminEmail,
        @Size(min = 8, max = 72) String adminPassword,
        @Size(max = 100) String adminName
) {
}
