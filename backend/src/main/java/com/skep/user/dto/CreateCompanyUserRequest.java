package com.skep.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 회사 master 가 같은 회사 하위 직원을 직접 등록할 때의 입력. role/companyId/isCompanyAdmin 은 서버에서 결정. */
public record CreateCompanyUserRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 32) String phone
) {
}
