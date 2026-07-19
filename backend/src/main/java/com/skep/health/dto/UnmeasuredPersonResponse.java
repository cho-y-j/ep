package com.skep.health.dto;

/** 오늘 혈압 미측정 고위험군(HIGH) 1인 — 관제 강조 대상. */
public record UnmeasuredPersonResponse(
        Long personId,
        String name,
        String healthRiskLevel,
        String phone
) {
}
