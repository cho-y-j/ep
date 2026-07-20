package com.skep.consultation.dto;

import jakarta.validation.constraints.NotBlank;

/** 공개 상담 요청 접수. JSON 전역 SNAKE_CASE — 필드는 camelCase record. email 은 선택. */
public record CreateConsultationRequest(
        @NotBlank String companyName,
        @NotBlank String contactName,
        @NotBlank String phone,
        String email,
        String message
) {}
