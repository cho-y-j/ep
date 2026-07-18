package com.skep.quotetemplate.dto;

import com.skep.quotetemplate.QuoteTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record QuoteTemplateResponse(
        Long id,
        Long supplierCompanyId,
        String name,
        String memo,
        List<Map<String, Object>> rows,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static QuoteTemplateResponse from(QuoteTemplate t) {
        return new QuoteTemplateResponse(
                t.getId(), t.getSupplierCompanyId(), t.getName(), t.getMemo(),
                t.getRows() != null ? t.getRows() : List.of(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
