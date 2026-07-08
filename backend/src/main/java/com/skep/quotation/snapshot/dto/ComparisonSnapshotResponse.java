package com.skep.quotation.snapshot.dto;

import com.skep.quotation.snapshot.ComparisonSnapshot;

import java.time.LocalDateTime;

public record ComparisonSnapshotResponse(
        Long id,
        Long quotationRequestId,
        Long selectedProposalId,
        LocalDateTime selectedAt,
        String snapshotJson,
        String selectionReason
) {
    public static ComparisonSnapshotResponse from(ComparisonSnapshot s) {
        return new ComparisonSnapshotResponse(
                s.getId(),
                s.getQuotationRequestId(),
                s.getSelectedProposalId(),
                s.getSelectedAt(),
                s.getSnapshotJson(),
                s.getSelectionReason()
        );
    }
}
