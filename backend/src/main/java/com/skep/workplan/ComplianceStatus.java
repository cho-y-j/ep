package com.skep.workplan;

/**
 * 작업계획서 자원 추가 시점 컴플라이언스 스냅샷 상태.
 *
 * - OK         : 모든 필수+blocks_assignment 서류 유효
 * - WARNING    : 만료 임박(<=30일) 등 주의는 있지만 차단은 아님
 * - BLOCKED    : 필수 서류 누락/만료/REJECTED — 기본 차단
 * - OVERRIDDEN : ADMIN 이 사유 남기고 강제 진행
 */
public enum ComplianceStatus {
    OK,
    WARNING,
    BLOCKED,
    OVERRIDDEN
}
