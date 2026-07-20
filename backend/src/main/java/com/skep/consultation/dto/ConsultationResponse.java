package com.skep.consultation.dto;

import com.skep.consultation.Consultation;

import java.time.LocalDateTime;

public record ConsultationResponse(
        Long id,
        String companyName,
        String contactName,
        String phone,
        String email,
        String message,
        boolean handled,
        LocalDateTime createdAt
) {
    public static ConsultationResponse from(Consultation c) {
        return new ConsultationResponse(
                c.getId(), c.getCompanyName(), c.getContactName(), c.getPhone(),
                c.getEmail(), c.getMessage(), c.isHandled(), c.getCreatedAt());
    }
}
