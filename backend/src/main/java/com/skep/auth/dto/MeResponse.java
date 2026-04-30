package com.skep.auth.dto;

import com.skep.company.dto.CompanyResponse;

public record MeResponse(
        UserResponse user,
        CompanyResponse company
) {
}
