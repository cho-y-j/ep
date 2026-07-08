package com.skep.assignment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 장비/인원 현장 배치 요청.
 *
 * - override: ADMIN 만 사용 가능. 서류 미비(`DOCUMENTS_BLOCKED`)를 강제 진행할 때 true.
 * - override_reason: override 사용 시 필수. audit/감사용.
 */
public record AssignRequest(
        @NotNull Long siteId,
        @Size(max = 255) String note,
        Boolean override,
        @Size(max = 255) String overrideReason
) {
}
