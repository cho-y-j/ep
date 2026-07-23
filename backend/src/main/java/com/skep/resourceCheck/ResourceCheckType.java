package com.skep.resourceCheck;

public enum ResourceCheckType {
    VEHICLE_SAFETY,    // 자동차 반입검사 (장비) — V125 명칭 정정, 코드값 불변
    HEALTH_CHECK,      // 건강검진 (인원)
    SAFETY_TRAINING,   // 안전교육 (인원)
    OTHER              // 기타
}
