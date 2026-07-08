package com.skep.quotation.dispatch.dto;

import com.skep.quotation.dispatch.DispatchedPerson;

import java.time.LocalDateTime;

public record DispatchedPersonResponse(
        Long id,
        Long quotationRequestId,
        Long supplierCompanyId,
        String supplierCompanyName,
        Long personId,
        String personLabel,    // 이름
        String jobTitle,
        Long dailyPrice,
        Long monthlyPrice,
        String notes,
        LocalDateTime sentAt
) {
    public static DispatchedPersonResponse from(DispatchedPerson d, String supplierName, String personLabel, String jobTitle) {
        return new DispatchedPersonResponse(
                d.getId(),
                d.getQuotationRequestId(),
                d.getSupplierCompanyId(),
                supplierName,
                d.getPersonId(),
                personLabel,
                jobTitle,
                d.getDailyPrice(),
                d.getMonthlyPrice(),
                d.getNotes(),
                d.getSentAt()
        );
    }
}
