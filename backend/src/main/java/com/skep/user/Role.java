package com.skep.user;

public enum Role {
    ADMIN,
    BP,
    EQUIPMENT_SUPPLIER,
    MANPOWER_SUPPLIER,
    WORKER,
    CLIENT;   // 원청(SK 등) 현장 통합 관제 — 회사 대신 client_org 에 소속, 읽기전용.

    public boolean requiresCompany() {
        return this == BP || this == EQUIPMENT_SUPPLIER || this == MANPOWER_SUPPLIER;
    }
}
