package com.skep.dailywork.dto;

import com.skep.contract.RateType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** 일일 확인서 생성·수정 공용 요청. 전표 사진·서명은 별도 엔드포인트. */
public record SaveDailyWorkLogRequest(
        @NotNull LocalDate workDate,
        Long siteId,
        String siteName,
        Long bpCompanyId,
        Long contractId,
        Long equipmentId,
        Long personId,
        String workContent,
        String workLocation,
        RateType rateType,
        BigDecimal otEarly,
        BigDecimal otLunch,
        BigDecimal otEvening,
        BigDecimal otNight,
        BigDecimal otOvernight,
        LocalTime startTime,
        LocalTime endTime,
        String memo
) {}
