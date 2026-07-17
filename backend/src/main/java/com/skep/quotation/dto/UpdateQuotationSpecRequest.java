package com.skep.quotation.dto;

import com.skep.person.PersonRole;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * V33: BP 가 견적 spec 수정 (공개입찰/지정배차 공통).
 * spec 변경 시 active proposals 는 PENDING_REVIEW 로 자동 전환.
 */
public record UpdateQuotationSpecRequest(
        String equipmentCategory,
        PersonRole manpowerRole,
        Long clientOrgId,
        @Size(max = 1000) String workLocationText,
        @Size(max = 4000) String specText,
        Integer proposedDailyRate,
        Integer proposedMonthlyRate,
        LocalDate workPeriodStart,
        LocalDate workPeriodEnd,
        Integer count,
        @Size(max = 4000) String notes
) {}
