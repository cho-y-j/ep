package com.skep.resourceCheck.dto;

import com.skep.resourceCheck.ResourceCheckType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** R2 조합 일괄 발행 — 장비 1대 + 조종원 N명에 종류별 점검을 단일 트랜잭션으로 발행. */
public record IssueComboRequest(
        @NotNull Long equipmentId,
        List<Long> operatorPersonIds,
        @NotNull Long supplierCompanyId,
        Long workPlanId,
        LocalDate dueDate,
        String notes,
        @NotNull Checks checks
) {
    /** 장비/조종원 각각 발행할 점검 종류(장비 1×N종 + 조종원 N×M종). */
    public record Checks(List<ResourceCheckType> equipment, List<ResourceCheckType> operator) {}
}
