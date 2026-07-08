package com.skep.field.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterTokenRequest(
        @NotNull Long workerId,
        @NotBlank String token
) {
}
