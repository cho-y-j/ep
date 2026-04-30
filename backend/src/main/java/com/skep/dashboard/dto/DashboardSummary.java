package com.skep.dashboard.dto;

import java.util.List;

public record DashboardSummary(
        Counts counts,
        List<ExpiringDocumentItem> expiringDocuments
) {
    public record Counts(
            Long persons,
            Long equipment,
            Long companies,
            Long usersPending,
            Long documentsExpiring30d,
            Long documentsUnverified
    ) {}
}
