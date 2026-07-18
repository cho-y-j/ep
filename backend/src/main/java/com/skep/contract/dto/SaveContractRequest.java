package com.skep.contract.dto;

import com.skep.contract.RateType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** 계약 생성·수정 공용 요청. 계약서 파일은 별도 multipart 엔드포인트로. */
public record SaveContractRequest(
        Long bpCompanyId,
        String bpName,
        Long siteId,
        String siteName,
        String title,
        String equipmentDesc,
        @NotNull RateType rateType,
        Long baseRate,
        Long rateEarly,
        Long rateLunch,
        Long rateEvening,
        Long rateNight,
        Long rateOvernight,
        LocalDate startDate,
        LocalDate endDate,
        String memo
) {}
