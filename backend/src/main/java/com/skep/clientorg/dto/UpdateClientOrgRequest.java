package com.skep.clientorg.dto;

import jakarta.validation.constraints.Size;

public record UpdateClientOrgRequest(
        @Size(max = 100) String name,
        @Size(max = 32) String code,
        String note
) {}
