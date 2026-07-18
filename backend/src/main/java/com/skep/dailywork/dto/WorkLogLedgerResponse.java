package com.skep.dailywork.dto;

import com.skep.contract.RateType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 월간 작업확인 원장(§3.6.3 실양식) — 정산주기(현장정산일 26~25) 내 일별 행 + 합계.
 * 계약 연결 시 분류별 단가×시간 금액·기본단가 금액 포함.
 */
public record WorkLogLedgerResponse(
        String period,           // 요청 기준월 (YYYY-MM)
        LocalDate startDate,     // 정산주기 시작일
        LocalDate endDate,       // 정산주기 종료일
        Integer settlementDay,   // 현장정산일(없으면 null → 달력월)
        Long siteId,
        String siteName,
        Long equipmentId,
        String equipmentLabel,
        Long personId,
        String personName,
        Long contractId,
        RateType rateType,
        Long baseRate,
        Rates otRates,
        List<Row> rows,
        Totals totals
) {
    public record Rates(Long early, Long lunch, Long evening, Long night, Long overnight) {}

    public record Row(
            Long id,
            LocalDate workDate,
            String workContent,
            BigDecimal otEarly,
            BigDecimal otLunch,
            BigDecimal otEvening,
            BigDecimal otNight,
            BigDecimal otOvernight,
            String signStatus,
            String memo
    ) {}

    public record Totals(
            int workDays,
            BigDecimal otEarlyHours,
            BigDecimal otLunchHours,
            BigDecimal otEveningHours,
            BigDecimal otNightHours,
            BigDecimal otOvernightHours,
            Long baseAmount,     // 계약 없으면 null
            Long otAmount,       // 계약 없으면 null
            Long totalAmount     // 계약 없으면 null
    ) {}
}
