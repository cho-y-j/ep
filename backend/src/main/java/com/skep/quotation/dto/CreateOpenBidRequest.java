package com.skep.quotation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skep.person.PersonRole;
import com.skep.quotation.QuotationRequestType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * V33: 공개입찰 견적 발송 (site 없이 시작 가능).
 *  - clientOrgId: 옵션 (없으면 양식상 "협의")
 *  - workLocationText: 현장 이름/주소 자유 텍스트
 *  - proposedDailyRate/MonthlyRate: 옵션 (BP 예산. 없으면 공급사 자유 단가)
 *  - emailRecipients: 옵션. CSV 또는 줄바꿈 구분 이메일 주소. 입력 시 같은 양식 PDF 메일 발송.
 */
public record CreateOpenBidRequest(
        @JsonProperty("request_type") QuotationRequestType requestType,
        @JsonProperty("equipment_category") @Size(max = 32) String equipmentCategory,
        @JsonProperty("manpower_role") PersonRole manpowerRole,
        @JsonProperty("client_org_id") Long clientOrgId,
        @JsonProperty("work_location_text") @Size(max = 1000) String workLocationText,
        @JsonProperty("spec_text") @Size(max = 4000) String specText,
        @JsonProperty("proposed_daily_rate") Integer proposedDailyRate,
        @JsonProperty("proposed_monthly_rate") Integer proposedMonthlyRate,
        @JsonProperty("work_period_start") @NotNull LocalDate workPeriodStart,
        @JsonProperty("work_period_end") @NotNull LocalDate workPeriodEnd,
        Integer count,
        @Size(max = 4000) String notes,
        @JsonProperty("on_behalf_of_bp_company_id") Long onBehalfOfBpCompanyId,
        @JsonProperty("email_recipients") @Size(max = 2000) String emailRecipients
) {}
