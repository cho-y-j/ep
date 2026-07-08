package com.skep.workconfirmation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 월별 작업확인서 — 인원 단위로 한 달치 일별 작업확인서를 집계한 결과.
 * 일별(WorkConfirmation)을 그대로 합산한 읽기 전용 뷰 (별도 저장 없음).
 */
public record MonthlyWorkConfirmationResponse(
        Long personId,
        String personName,
        Long issuingSupplierCompanyId,
        String supplierName,
        Long bpCompanyId,
        String bpName,
        int year,
        int month,
        int totalDays,
        BigDecimal morningHours,
        BigDecimal afternoonHours,
        BigDecimal overtimeHours,
        BigDecimal nightHours,
        BigDecimal totalHours,
        List<DailyRow> days
) {
    public record DailyRow(
            Long id,
            LocalDate workDate,
            BigDecimal morningHours,
            BigDecimal afternoonHours,
            BigDecimal overtimeHours,
            BigDecimal nightHours,
            BigDecimal totalHours,
            String workContent,
            boolean supplierSigned,
            boolean bpSigned
    ) {}
}
