package com.skep.workplan.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 작업계획서 복제 요청. 모든 필드 optional.
 *  - workDate: null 이면 원본 work_date + 1일
 *  - title: null 이면 "[복사] " + 원본 제목
 */
public record CloneWorkPlanRequest(
        LocalDate workDate,
        @Size(max = 150) String title
) {
}
