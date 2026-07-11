package com.skep.readiness;

import java.util.List;

/**
 * 투입 준비 상태(읽기전용). 저장/마이그레이션 없음 — 작업계획서 게이트 판정을 자원 단위로 미러링한 산출값.
 * - ready: 게이트 3종(점검 전부 APPROVED + 안전점검 COMPLETED + 미해결 이행지시 없음) 동일 판정 통과.
 * - pending: 준비중이면 남은 사유 목록(예: "자동차 안전점검 미승인", "안전점검 미완", "이행지시 2건").
 */
public record ResourceReadinessResponse(
        String resourceType,   // "EQUIPMENT" | "PERSON"
        Long resourceId,
        String label,
        boolean ready,
        List<String> pending
) {}
