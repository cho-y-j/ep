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
            // BP 회사도 자체 인원 보유 가능 (직속 운전수/지휘자 등 모든 역할) — #5 정책 확장
            case BP -> true;
        };
    }

    public static Set<PersonRole> allowedFor(CompanyType companyType) {
        return switch (companyType) {
            case EQUIPMENT -> Set.of(OPERATOR);
            case MANPOWER -> Set.of(WORK_DIRECTOR, GUIDE, FIRE_WATCH, SIGNALER, INSPECTOR, SITE_MANAGER);
            // BP 회사 자체 인원 — 모든 역할 허용
            case BP -> Set.of(OPERATOR, WORK_DIRECTOR, GUIDE, FIRE_WATCH, SIGNALER, INSPECTOR, SITE_MANAGER);
        };
    }
}
