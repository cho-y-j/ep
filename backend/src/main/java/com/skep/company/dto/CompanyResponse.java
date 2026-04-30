package com.skep.company.dto;

import com.skep.company.Company;
import com.skep.company.CompanyType;

import java.time.LocalDateTime;

public record CompanyResponse(
        Long id,
        String name,
        String businessNumber,
        CompanyType type,
        LocalDateTime createdAt
) {
    public static CompanyResponse from(Company c) {
        return new CompanyResponse(c.getId(), c.getName(), c.getBusinessNumber(), c.getType(), c.getCreatedAt());
    }
}
