package com.skep.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Phase4: 외부 장비 기사(조종원) 등록 + 로그인 계정 발급. */
public record RegisterOperatorRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 32) String phone,
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 100) String password
) {
}
