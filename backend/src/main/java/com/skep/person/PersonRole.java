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
            // 장비공급사·BP 는 자체 인력을 모든 역할로 등록 가능(조종원 외 신호수·유도원 등). 인력공급사는 인력역할만(조종원 제외).
            case EQUIPMENT, BP -> true;
            case MANPOWER -> this != OPERATOR;
        };
    }

    public static Set<PersonRole> allowedFor(CompanyType companyType) {
        return switch (companyType) {
            case MANPOWER -> Set.of(WORK_DIRECTOR, GUIDE, FIRE_WATCH, SIGNALER, INSPECTOR, SITE_MANAGER);
            // 장비공급사·BP — 모든 역할 허용
            case EQUIPMENT, BP -> Set.of(OPERATOR, WORK_DIRECTOR, GUIDE, FIRE_WATCH, SIGNALER, INSPECTOR, SITE_MANAGER);
        };
    }
}
