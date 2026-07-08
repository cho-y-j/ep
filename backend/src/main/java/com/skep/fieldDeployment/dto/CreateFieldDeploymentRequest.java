package com.skep.fieldDeployment.dto;

import com.skep.document.OwnerType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateFieldDeploymentRequest(
        @NotNull Long bpCompanyId,
        @NotNull OwnerType resourceType,
        @NotNull Long resourceId,
        Long targetSiteId,
        LocalDate startDate,
        String note,
        Long dailyPrice,
        Long monthlyPrice,
        Long otPrice,
        Long nightPrice
) {}
