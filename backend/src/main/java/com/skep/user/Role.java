package com.skep.user;

public enum Role {
    ADMIN,
    BP,
    EQUIPMENT_SUPPLIER,
    MANPOWER_SUPPLIER,
    WORKER;

    public boolean requiresCompany() {
        return this == BP || this == EQUIPMENT_SUPPLIER || this == MANPOWER_SUPPLIER;
    }
}
