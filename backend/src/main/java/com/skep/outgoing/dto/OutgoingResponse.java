package com.skep.outgoing.dto;

import com.skep.outgoing.OutgoingQuotation;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record OutgoingResponse(
        Long id,
        Long supplierCompanyId,
        String supplierCompanyName,
        Long equipmentId,
        String equipmentLabel,
        Long personId,
        String personLabel,
        Integer dailyRate,
        Integer monthlyRate,
        String note,
        LocalDate periodStart,
        LocalDate periodEnd,
        String recipientType,
        Long recipientUserId,
        Long recipientCompanyId,
        String recipientCompanyName,
        String recipientEmail,
        LocalDateTime sentAt,
        boolean mailSent,
        String mailError,
        // V37: BP 수락 사인
        boolean bpSigned,
        String bpSignerName,
        LocalDateTime bpSignedAt
) {
    public static OutgoingResponse from(OutgoingQuotation o, String supplierName,
                                         String equipmentLabel, String personLabel,
                                         String recipientCompanyName) {
        return new OutgoingResponse(
                o.getId(), o.getSupplierCompanyId(), supplierName,
                o.getEquipmentId(), equipmentLabel,
                o.getPersonId(), personLabel,
                o.getDailyRate(), o.getMonthlyRate(), o.getNote(),
                o.getPeriodStart(), o.getPeriodEnd(),
                o.getRecipientType().name(),
                o.getRecipientUserId(), o.getRecipientCompanyId(), recipientCompanyName,
                o.getRecipientEmail(),
                o.getSentAt(), o.isMailSent(), o.getMailError(),
                o.isBpSigned(), o.getBpSignerName(), o.getBpSignedAt()
        );
    }
}
