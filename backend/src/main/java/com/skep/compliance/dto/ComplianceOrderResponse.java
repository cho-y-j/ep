package com.skep.compliance.dto;

import com.skep.compliance.ComplianceOrder;
import com.skep.compliance.ComplianceOrderStatus;
import com.skep.compliance.ComplianceOrderType;
import com.skep.compliance.ComplianceTargetType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ComplianceOrderResponse(
        Long id,
        Long bpCompanyId,
        String bpCompanyName,
        Long supplierCompanyId,
        String supplierCompanyName,
        ComplianceTargetType targetType,
        Long targetId,
        String targetLabel,
        ComplianceOrderType orderType,
        String orderSubtype,
        LocalDate dueDate,
        String requestNotes,
        ComplianceOrderStatus status,
        boolean overdue,
        LocalDateTime submittedAt,
        String submissionNotes,
        String proofFilename,
        String proofContentType,
        LocalDateTime reviewedAt,
        Long reviewedBy,
        String rejectionReason,
        LocalDateTime createdAt
) {
    public static ComplianceOrderResponse from(ComplianceOrder o, String bpName, String supplierName, String targetLabel) {
        boolean overdue = o.isOverdue(LocalDate.now());
        return new ComplianceOrderResponse(
                o.getId(), o.getBpCompanyId(), bpName,
                o.getSupplierCompanyId(), supplierName,
                o.getTargetType(), o.getTargetId(), targetLabel,
                o.getOrderType(), o.getOrderSubtype(), o.getDueDate(), o.getRequestNotes(),
                o.getStatus(), overdue,
                o.getSubmittedAt(), o.getSubmissionNotes(),
                o.getProofFilename(), o.getProofContentType(),
                o.getReviewedAt(), o.getReviewedBy(), o.getRejectionReason(),
                o.getCreatedAt()
        );
    }
}
