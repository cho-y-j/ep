package com.skep.compliance.dto;

import jakarta.validation.constraints.NotNull;

public record ReviewComplianceRequest(
        @NotNull Boolean approve,
        String rejectionReason
) {}
