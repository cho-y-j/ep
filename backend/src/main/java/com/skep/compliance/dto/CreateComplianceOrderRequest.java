package com.skep.compliance.dto;

import com.skep.compliance.ComplianceOrderType;
import com.skep.compliance.ComplianceTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateComplianceOrderRequest(
        @NotNull Long supplierCompanyId,
        @NotNull ComplianceTargetType targetType,
        @NotNull Long targetId,
        @NotNull ComplianceOrderType orderType,
        @Size(max = 100) String orderSubtype,
        @NotNull LocalDate dueDate,
        String requestNotes
) {}
