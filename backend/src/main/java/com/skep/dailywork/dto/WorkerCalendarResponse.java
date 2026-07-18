package com.skep.dailywork.dto;

import com.skep.contract.RateType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 작업자 본인 "내 작업 달력"(§0-A #3) — 정산주기(현장정산일 26~25) 창 + 근무일 + 합계.
 * period(YYYY-MM) 미지정 시 오늘이 속한 주기. settlementDay 는 인원 로그의 현장에서 해석(없으면 달력월).
 */
public record WorkerCalendarResponse(
        String period,          // 앵커 기준월 (YYYY-MM)
        LocalDate cycleStart,   // 정산주기 시작일
        LocalDate cycleEnd,     // 정산주기 종료일
        Integer settlementDay,  // 현장정산일(없으면 null → 달력월)
        List<Day> days,         // 주기 내 근무일 (오름차순)
        Totals totals
) {
    public record Day(
            Long id,
            LocalDate workDate,
            String workContent,
            String workLocation,
            String siteName,
            RateType rateType,
            BigDecimal otEarly,
            BigDecimal otLunch,
            BigDecimal otEvening,
            BigDecimal otNight,
            BigDecimal otOvernight,
            BigDecimal otTotal,
            LocalTime startTime,
            LocalTime endTime,
            String signStatus,   // UNSIGNED | SIGNED | PHOTO
            String memo
    ) {}

    public record Totals(
            int workDays,
            BigDecimal otEarlyHours,
            BigDecimal otLunchHours,
            BigDecimal otEveningHours,
            BigDecimal otNightHours,
            BigDecimal otOvernightHours,
            BigDecimal otTotalHours,
            int signedCount,
            int photoCount,
            int unsignedCount
    ) {}
}
