package com.skep.company;

import com.skep.user.Role;

public enum CompanyType {
    BP,
    EQUIPMENT,
    MANPOWER;

    public static CompanyType fromRole(Role role) {
        return switch (role) {
            case BP -> BP;
            case EQUIPMENT_SUPPLIER -> EQUIPMENT;
            case MANPOWER_SUPPLIER -> MANPOWER;
            default -> throw new IllegalArgumentException("role has no company type: " + role);
        };
    }
}
