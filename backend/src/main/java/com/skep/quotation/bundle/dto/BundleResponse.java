package com.skep.quotation.bundle.dto;

import com.skep.quotation.bundle.DocumentBundle;

import java.time.LocalDateTime;

public record BundleResponse(
        Long id,
        Long quotationRequestId,
        Long supplierCompanyId,
        String supplierCompanyName,
        LocalDateTime sentAt,
        boolean includeEmail,
        LocalDateTime emailSentAt,
        String notes
) {
    public static BundleResponse from(DocumentBundle b, String supplierName) {
        return new BundleResponse(
                b.getId(), b.getQuotationRequestId(), b.getSupplierCompanyId(), supplierName,
                b.getSentAt(), b.isIncludeEmail(), b.getEmailSentAt(), b.getNotes()
        );
    }
}
