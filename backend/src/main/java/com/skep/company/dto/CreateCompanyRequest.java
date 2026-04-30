package com.skep.company.dto;

import com.skep.company.CompanyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 32) String businessNumber,
        @NotNull CompanyType type
) {
}
