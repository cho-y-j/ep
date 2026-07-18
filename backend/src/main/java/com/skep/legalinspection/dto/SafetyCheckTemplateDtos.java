package com.skep.legalinspection.dto;

import com.skep.legalinspection.SafetyCheckTemplate;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 점검 템플릿 CRUD DTO. items = [{no, text, required}] 패스스루. */
public final class SafetyCheckTemplateDtos {

    private SafetyCheckTemplateDtos() {}

    public record SaveRequest(
            @NotBlank String name,
            String target,                 // null → EQUIPMENT
            List<Map<String, Object>> items,
            Boolean active
    ) {}

    public record Response(
            Long id,
            String name,
            String target,
            List<Map<String, Object>> items,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static Response from(SafetyCheckTemplate t) {
            return new Response(t.getId(), t.getName(), t.getTarget(),
                    t.getItems() != null ? t.getItems() : List.of(),
                    t.isActive(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }
}
