package com.skep.clientorg.history;

import java.time.LocalDate;

public record HistoryDto(
        Long id,
        Long clientOrgId,
        String clientOrgName,
        LocalDate periodStart,
        LocalDate periodEnd,
        HistorySource source
) {}
