package com.skep.company.dto;

import jakarta.validation.constraints.Size;

/** 견적서 양식에 자동 채울 회사 프로필 필드. 모두 선택 입력. */
public record UpdateCompanyProfileRequest(
        @Size(max = 255) String businessAddress,
        @Size(max = 100) String businessCategory,
        @Size(max = 200) String businessSubcategory,
        @Size(max = 100) String ceoName,
        @Size(max = 32) String phone,
        @Size(max = 32) String fax
) {}
