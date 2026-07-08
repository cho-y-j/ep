package com.skep.safety;

public enum InspectionStatus {
    PENDING,    // 등록만 됨
    SENT,       // BP 가 공급사에 통보
    CONFIRMED,  // 공급사 확인
    COMPLETED,  // 검사 완료 (작업 시작 게이트 통과)
    CANCELLED
}
