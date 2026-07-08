package com.skep.quotation.dto;

import com.skep.equipment.EquipmentCategory;
import com.skep.person.PersonRole;
import com.skep.quotation.QuotationMode;
import com.skep.quotation.QuotationRequestType;
import com.skep.quotation.QuotationStatus;
import com.skep.quotation.QuotationTargetStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record QuotationRequestResponse(
        Long id,
        Long siteId,
        String siteName,
        Long bpCompanyId,
        String bpCompanyName,
        Long requestedByUserId,
        String requestedByUserName,
        Long onBehalfOfBpCompanyId,
        LocalDate workPeriodStart,
        LocalDate workPeriodEnd,
        QuotationRequestType requestType,
        EquipmentCategory equipmentCategory,
        PersonRole manpowerRole,
        String specText,
        Integer proposedDailyRate,
        Integer proposedMonthlyRate,
        Integer count,
        String notes,
        QuotationStatus status,
        UUID bundleId,
        QuotationMode mode,
        Long clientOrgId,
        String clientOrgName,
        String workLocationText,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<TargetItem> targets
) {
    public record TargetItem(
            Long id,
            Long supplierCompanyId,
            String supplierCompanyName,
            Long equipmentId,
            String equipmentLabel,
            Long personId,
            String personLabel,
            QuotationTargetStatus status,
            Long respondedByUserId,
            LocalDateTime respondedAt,
            String responseNote,
            Long finalizedByUserId,
            LocalDateTime finalizedAt,
            Long finalizedToWorkPlanId,
            Long finalizedToWpeId
    ) {}
}
