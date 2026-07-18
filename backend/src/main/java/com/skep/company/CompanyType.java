package com.skep.company;

import com.skep.user.Role;

public enum CompanyType {
    BP,
    EQUIPMENT,
    MANPOWER,
    SAFETY_INSPECTION;   // 안전점검회사 — 소속 점검원이 S2′ 법정점검(NFC) 수행. 견적/배차/정산/서류심사 불참.

    public static CompanyType fromRole(Role role) {
        return switch (role) {
            case BP -> BP;
            case EQUIPMENT_SUPPLIER -> EQUIPMENT;
            case MANPOWER_SUPPLIER -> MANPOWER;
            default -> throw new IllegalArgumentException("role has no company type: " + role);
        };
    }
}
