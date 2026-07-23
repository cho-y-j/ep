package com.skep.resourceCheck.dto;

import com.skep.document.OwnerType;
import com.skep.resourceCheck.ResourceCheckType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record IssueRequest(
        Long workPlanId,
        @NotNull OwnerType ownerType,
        @NotNull Long ownerId,
        @NotNull Long supplierCompanyId,
        @NotNull ResourceCheckType checkType,
        LocalDate dueDate,
        LocalTime dueTime,
        String notes,
        List<String> alimtalkPhones
) {}
