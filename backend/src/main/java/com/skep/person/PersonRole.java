package com.skep.person;

import com.skep.company.CompanyType;

import java.util.Set;

public enum PersonRole {
    OPERATOR,           // 조종원       — 장비공급사 소속
    WORK_DIRECTOR,      // 작업지휘자   — 인력공급사
    GUIDE,              // 유도원       — 인력공급사
    FIRE_WATCH,         // 화기감시자   — 인력공급사
    SIGNALER,           // 신호수       — 인력공급사
    INSPECTOR,          // 점검원       — 인력공급사 (잠정)
    SITE_MANAGER;       // 소장         — 인력공급사 (잠정)

    public boolean isAllowedFor(CompanyType companyType) {
        return switch (companyType) {
            case EQUIPMENT -> this == OPERATOR;
            case MANPOWER -> this != OPERATOR;
            case BP -> false;
        };
    }

    public static Set<PersonRole> allowedFor(CompanyType companyType) {
        return switch (companyType) {
            case EQUIPMENT -> Set.of(OPERATOR);
            case MANPOWER -> Set.of(WORK_DIRECTOR, GUIDE, FIRE_WATCH, SIGNALER, INSPECTOR, SITE_MANAGER);
            case BP -> Set.of();
        };
    }
}
