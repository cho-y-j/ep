package com.skep.workplan.dto;

import jakarta.validation.constraints.Size;

/**
 * 작업 시작 요청. 다른 사이트에 이미 배치된 자원이 있을 때 ADMIN 만 force=true 로 강제 진행 가능.
 * 자원은 그 자리에 두고 plan 만 IN_PROGRESS 로 전환 (수동 이동 결정은 운영자에게 맡김).
 */
public record StartWorkPlanRequest(
        Boolean force,
        @Size(max = 255) String forceReason
) {
}
