package com.skep.safety;

public enum InspectionKind {
    /** 차량검사 — 현장 입소 며칠 사전. 장비 대상. */
    VEHICLE_INSPECTION,
    /** 입소검사 — 현장 도착 당일/직전. 인원/장비 모두 대상 (안전교육 + 입소 점검). */
    ENTRY_CHECK
}
