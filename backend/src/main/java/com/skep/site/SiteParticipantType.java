package com.skep.site;

import com.skep.company.CompanyType;

public enum SiteParticipantType {
    EQUIPMENT_SUPPLIER,
    MANPOWER_SUPPLIER;

    public static SiteParticipantType fromCompanyType(CompanyType type) {
        return switch (type) {
            case EQUIPMENT -> EQUIPMENT_SUPPLIER;
            case MANPOWER -> MANPOWER_SUPPLIER;
            case BP -> throw new IllegalArgumentException("BP company is site owner, not supplier participant");
        };
    }
}
