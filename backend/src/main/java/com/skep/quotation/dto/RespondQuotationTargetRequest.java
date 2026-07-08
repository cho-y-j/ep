package com.skep.quotation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** S-10: 공급사 응답. accept=true → ACCEPTED, false → REJECTED. */
public record RespondQuotationTargetRequest(
        @NotNull Boolean accept,
        @Size(max = 1000) String note
) {}
