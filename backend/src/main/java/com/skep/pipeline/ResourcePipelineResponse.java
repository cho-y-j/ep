package com.skep.pipeline;

/**
 * 자원 파이프라인(읽기전용 집계) — 자원 1건의 서류→검사→투입대기→투입→작업→정산 6단계 상태.
 * 각 단계는 기존 도메인 서비스/레포의 상태를 그대로 집계한 산출값이다. 저장/마이그레이션 없음.
 *
 * stage.state: "DONE"(완료) | "PENDING"(미달/미도달) | "NA"(해당없음, 예: 장비의 작업 단계).
 * stage.summary: 사람이 읽는 한 줄 요약(사유/근거).
 */
public record ResourcePipelineResponse(
        String resourceType,   // "EQUIPMENT" | "PERSON"
        Long resourceId,
        String label,
        Stages stages
) {
    public record Stages(
            Stage docs,        // 서류 — ComplianceService.ResourceCompliance
            Stage inspection,  // 검사 — ResourceCheck + SafetyInspection
            Stage readiness,   // 투입대기 — ResourceReadinessService (그대로 재사용)
            Stage deployed,    // 투입 — 배차행 존재 + FieldDeployment ACTIVE
            Stage work,        // 작업 — 최근 작업확인서(인력만)
            Stage settlement   // 정산 — 배차행 있으면 정산대상
    ) {}

    public record Stage(String state, String summary) {}
}
