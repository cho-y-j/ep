package com.skep.quotation.proposal.dto;

public record UpdateProposalRequest(
        Integer dailyRate,
        Integer otDailyRate,
        Integer monthlyRate,
        Integer otMonthlyRate,
        String note,
        String dailyNote,
        String otDailyNote,
        String monthlyNote,
        String otMonthlyNote
) {}
