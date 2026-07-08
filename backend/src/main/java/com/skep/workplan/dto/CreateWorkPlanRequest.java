package com.skep.workplan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateWorkPlanRequest(
        /** 현장 옵션 — null 가능. 그러면 workLocation 자유 텍스트만 사용. */
        Long siteId,
        /** siteId 가 null 일 때 필수. site 가 있으면 site.bp_company_id 가 우선. */
        Long bpCompanyId,
        @NotNull LocalDate workDate,
        LocalTime startTime,
        LocalTime endTime,
        @NotBlank @Size(max = 150) String title,
        @Size(max = 255) String workLocation,
        String description,
        /** 견적 ID — 있으면 그 견적의 dispatched 차량/인원이 자동으로 wp 에 추가됨. */
        Long fromQuotationRequestId
) {
}
