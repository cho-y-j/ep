package com.skep.workconfirmation;

/** 작업확인서 발급 공급사 타입. */
public enum IssuingSupplierType {
    EQUIPMENT,  // 장비공급사 — 공급사측 사인 = 운전수(Person)
    MANPOWER    // 인력공급사 — 공급사측 사인 = MANPOWER_SUPPLIER 계정(User)
}
