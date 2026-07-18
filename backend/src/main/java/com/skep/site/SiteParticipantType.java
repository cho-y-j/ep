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
            // 안전점검회사는 현장 참여 공급사가 아니다(점검원이 외부에서 방문 점검).
            case SAFETY_INSPECTION -> throw new IllegalArgumentException("safety inspection company is not a supplier participant");
        };
    }
}
