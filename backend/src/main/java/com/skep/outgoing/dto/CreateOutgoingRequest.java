package com.skep.outgoing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * 공급사가 BP에게 보내는 영업 견적 발송 요청.
 * 자원 (equipmentId 또는 personId 중 1) + 단가 + 수신자 (recipientUserId 또는 recipientEmail 중 1).
 * ccEmails: 외부 이메일 발송 시 참조(CC) 받을 추가 이메일 (선택).
 */
public record CreateOutgoingRequest(
        @JsonProperty("equipment_id") Long equipmentId,
        @JsonProperty("person_id") Long personId,
        @JsonProperty("daily_rate") Integer dailyRate,
        @JsonProperty("monthly_rate") Integer monthlyRate,
        String note,
        @JsonProperty("period_start") LocalDate periodStart,
        @JsonProperty("period_end") LocalDate periodEnd,
        @JsonProperty("recipient_user_id") Long recipientUserId,
        @JsonProperty("recipient_email") String recipientEmail,
        @JsonProperty("cc_emails") List<String> ccEmails,
        @NotNull String mode
) {}
