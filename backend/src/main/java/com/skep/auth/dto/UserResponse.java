package com.skep.auth.dto;

import com.skep.user.Role;
import com.skep.user.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String email,
        String name,
        String phone,
        Role role,
        Long companyId,
        boolean isCompanyAdmin,
        boolean enabled,
        boolean showInQuote,
        Integer quoteDisplayOrder,
        LocalDateTime createdAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getEmail(),
                u.getName(),
                u.getPhone(),
                u.getRole(),
                u.getCompanyId(),
                u.isCompanyAdmin(),
                u.isEnabled(),
                u.isShowInQuote(),
                u.getQuoteDisplayOrder(),
                u.getCreatedAt()
        );
    }
}
