package com.skep.quotation.proposal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 공급사 응찰. 자원(차량/인원) 지정은 선택 — 단가만으로 제출 가능.
 * 단가 4종(일대/OT일대/월대/OT월대) 중 1개 이상 필수.
 */
public record CreateProposalRequest(
        @JsonProperty("equipment_id") Long equipmentId,
        @JsonProperty("person_id") Long personId,
        @JsonProperty("daily_rate") Integer dailyRate,
        @JsonProperty("ot_daily_rate") Integer otDailyRate,
        @JsonProperty("monthly_rate") Integer monthlyRate,
        @JsonProperty("ot_monthly_rate") Integer otMonthlyRate,
        String note,
        @JsonProperty("daily_note") String dailyNote,
        @JsonProperty("ot_daily_note") String otDailyNote,
        @JsonProperty("monthly_note") String monthlyNote,
        @JsonProperty("ot_monthly_note") String otMonthlyNote
) {
    public boolean hasResource() {
        return equipmentId != null || personId != null;
    }
    public boolean hasAnyRate() {
        return dailyRate != null || otDailyRate != null
                || monthlyRate != null || otMonthlyRate != null;
    }
}
