package com.skep.security;

import com.skep.user.Role;

public record AuthenticatedUser(
        Long id,
        String email,
        String name,
        Role role,
        Long companyId,
        boolean isCompanyAdmin
) {
}
