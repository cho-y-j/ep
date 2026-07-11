package com.skep.company.dto;

/**
 * B4: 부모 master 기준 직속 자식 공급사별 롤업 요약(읽기전용).
 * - readinessReady/Pending: 자식 소유 자원의 투입 준비 완료/미완 수(게이트 술어 미러링).
 * - documentsExpiringSoon: 자식 소유 자원(장비·인원)의 30일 내 만료 임박 서류 수.
 * - pendingUsers: 자식 회사의 가입 대기(미활성) 유저 수.
 */
public record ChildRollupResponse(
        Long childCompanyId,
        String childCompanyName,
        long readinessReady,
        long readinessPending,
        long documentsExpiringSoon,
        long pendingUsers
) {}
