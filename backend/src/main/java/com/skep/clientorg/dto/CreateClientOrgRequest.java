package com.skep.clientorg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClientOrgRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 32) String code,
        String note
) {}
