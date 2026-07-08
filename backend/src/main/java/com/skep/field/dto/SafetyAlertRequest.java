package com.skep.field.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SafetyAlertRequest(
        @NotNull Long workerId,
        @NotBlank @Size(max = 32) String kind,
        Integer hr,
        Integer spo2,
        Double lat,
        Double lng
) {
}
