package com.skep.quotation.proposal.dto;

import com.skep.person.PersonRole;
import com.skep.quotation.QuotationMode;
import com.skep.quotation.QuotationRequest;
import com.skep.quotation.QuotationRequestType;
import com.skep.quotation.QuotationStatus;
import com.skep.quotation.proposal.QuotationProposal;
import com.skep.quotation.proposal.QuotationProposalStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProposalResponse(
        Long id,
        Long requestId,
        Long supplierCompanyId,
        String supplierCompanyName,
        Long proposedByUserId,
        Long equipmentId,
        String equipmentLabel,
        Long personId,
        String personLabel,
        Integer dailyRate,
        Integer otDailyRate,
        Integer monthlyRate,
        Integer otMonthlyRate,
        String note,
        String dailyNote,
        String otDailyNote,
        String monthlyNote,
        String otMonthlyNote,
        QuotationProposalStatus status,
        LocalDateTime createdAt,
        LocalDateTime finalizedAt,
        LocalDateTime rejectedAt,
        // ── 견적 요약 (공급사의 "내 제안" 페이지에서 한 줄로 보여주기 위함) ──
        Long requestBpCompanyId,
        String requestBpCompanyName,
        Long requestRequestedByUserId,
        String requestRequestedByUserName,
        QuotationRequestType requestType,
        String requestEquipmentCategory,
        PersonRole requestManpowerRole,
        LocalDate requestWorkPeriodStart,
        LocalDate requestWorkPeriodEnd,
        QuotationStatus requestStatus,
        QuotationMode requestMode
) {
    public static ProposalResponse from(QuotationProposal p, String supplierName,
                                         String equipmentLabel, String personLabel,
                                         QuotationRequest qr, String bpCompanyName,
                                         String requestedByUserName) {
        return new ProposalResponse(
                p.getId(), p.getRequestId(), p.getSupplierCompanyId(), supplierName,
                p.getProposedByUserId(),
                p.getEquipmentId(), equipmentLabel,
                p.getPersonId(), personLabel,
                p.getDailyRate(), p.getOtDailyRate(),
                p.getMonthlyRate(), p.getOtMonthlyRate(),
                p.getNote(),
                p.getDailyNote(), p.getOtDailyNote(),
                p.getMonthlyNote(), p.getOtMonthlyNote(),
                p.getStatus(),
                p.getCreatedAt(), p.getFinalizedAt(), p.getRejectedAt(),
                qr != null ? qr.getBpCompanyId() : null,
                bpCompanyName,
                qr != null ? qr.getRequestedByUserId() : null,
                requestedByUserName,
                qr != null ? qr.getRequestType() : null,
                qr != null ? qr.getEquipmentCategory() : null,
                qr != null ? qr.getManpowerRole() : null,
                qr != null ? qr.getWorkPeriodStart() : null,
                qr != null ? qr.getWorkPeriodEnd() : null,
                qr != null ? qr.getStatus() : null,
                qr != null ? qr.getMode() : null
        );
    }
}
