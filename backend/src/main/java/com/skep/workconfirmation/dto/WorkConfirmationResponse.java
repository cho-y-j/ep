package com.skep.workconfirmation.dto;

import com.skep.workconfirmation.IssuingSupplierType;
import com.skep.workconfirmation.WorkConfirmation;
import com.skep.workconfirmation.WorkConfirmationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;

public record WorkConfirmationResponse(
        Long id,
        Long workPlanId,
        LocalDate workDate,
        Long personId,
        Long issuingSupplierCompanyId,
        IssuingSupplierType issuingSupplierType,
        Long bpCompanyId,
        String workContent,
        String remarks,
        String morningTime,
        BigDecimal morningHours,
        String afternoonTime,
        BigDecimal afternoonHours,
        String overtimeTime,
        BigDecimal overtimeHours,
        String nightTime,
        BigDecimal nightHours,
        BigDecimal totalHours,
        String supplierSignerName,
        Long supplierSignerPersonId,
        Long supplierSignerUserId,
        boolean supplierSigned,
        LocalDateTime supplierSignedAt,
        String supplierSignaturePngBase64,
        String bpSignerName,
        Long bpSignerUserId,
        boolean bpSigned,
        LocalDateTime bpSignedAt,
        String bpSignaturePngBase64,
        WorkConfirmationStatus status,
        Long attendancePhotoDocId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        // A1-sug: 비저장 OT 제안값 — 상세 조회에서만 채워지고(그 외 null), 사인 화면 프리필용. 저장·정산 미반영.
        BigDecimal suggestedOvertimeHours
) {
    public static WorkConfirmationResponse from(WorkConfirmation wc, boolean includePng) {
        return from(wc, includePng, null);
    }

    public static WorkConfirmationResponse from(WorkConfirmation wc, boolean includePng,
                                                BigDecimal suggestedOvertimeHours) {
        String sup = (includePng && wc.getSupplierSignaturePng() != null)
                ? Base64.getEncoder().encodeToString(wc.getSupplierSignaturePng()) : null;
        String bp = (includePng && wc.getBpSignaturePng() != null)
                ? Base64.getEncoder().encodeToString(wc.getBpSignaturePng()) : null;
        return new WorkConfirmationResponse(
                wc.getId(), wc.getWorkPlanId(), wc.getWorkDate(),
                wc.getPersonId(),
                wc.getIssuingSupplierCompanyId(), wc.getIssuingSupplierType(), wc.getBpCompanyId(),
                wc.getWorkContent(), wc.getRemarks(),
                wc.getMorningTime(), wc.getMorningHours(),
                wc.getAfternoonTime(), wc.getAfternoonHours(),
                wc.getOvertimeTime(), wc.getOvertimeHours(),
                wc.getNightTime(), wc.getNightHours(), wc.getTotalHours(),
                wc.getSupplierSignerName(), wc.getSupplierSignerPersonId(), wc.getSupplierSignerUserId(),
                wc.getSupplierSignedAt() != null, wc.getSupplierSignedAt(), sup,
                wc.getBpSignerName(), wc.getBpSignerUserId(),
                wc.getBpSignedAt() != null, wc.getBpSignedAt(), bp,
                wc.getStatus(), wc.getAttendancePhotoDocId(),
                wc.getCreatedAt(), wc.getUpdatedAt(),
                suggestedOvertimeHours
        );
    }
}
