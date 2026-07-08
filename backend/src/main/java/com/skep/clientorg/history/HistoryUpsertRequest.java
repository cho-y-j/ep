package com.skep.clientorg.history;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** ADMIN 수동 등록/수정 요청. */
public record HistoryUpsertRequest(
        @NotNull Long clientOrgId,
        @NotNull LocalDate periodStart,
        LocalDate periodEnd
) {}
