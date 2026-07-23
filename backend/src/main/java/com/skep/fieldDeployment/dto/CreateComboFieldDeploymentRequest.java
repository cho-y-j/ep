package com.skep.fieldDeployment.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** R3 조합 투입 요청 — 장비 1행 + 조종원 N행을 단일 트랜잭션으로 생성(단가는 행 단위). */
public record CreateComboFieldDeploymentRequest(
        @NotNull Long bpCompanyId,
        @NotNull Long equipmentId,
        List<Long> operatorPersonIds,
        Long targetSiteId,
        LocalDate startDate,
        String note,
        Prices equipmentPrices,
        List<OperatorPrice> operatorPrices
) {
    public record Prices(Long dailyPrice, Long monthlyPrice, Long otPrice, Long nightPrice) {}
    public record OperatorPrice(Long personId, Long dailyPrice, Long monthlyPrice, Long otPrice, Long nightPrice) {}
}
