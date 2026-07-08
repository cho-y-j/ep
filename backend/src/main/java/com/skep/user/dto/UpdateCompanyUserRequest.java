package com.skep.user.dto;

import jakarta.validation.constraints.Size;

/** 회사 master 가 자기 회사 직원 프로필 수정. role/email/companyId 변경 불가. */
public record UpdateCompanyUserRequest(
        @Size(min = 1, max = 100) String name,
        @Size(max = 32) String phone,
        @Size(min = 8, max = 72) String newPassword,
        /** 견적서 우측 담당자 목록 노출 여부. */
        Boolean showInQuote,
        /** 견적서 노출 순서(낮은 값이 위, NULL은 마지막). */
        Integer quoteDisplayOrder
) {
}
