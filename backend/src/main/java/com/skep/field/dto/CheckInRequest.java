package com.skep.field.dto;

import jakarta.validation.constraints.NotNull;

public record CheckInRequest(
        @NotNull Long workerId,
        @NotNull Long siteId,
        @NotNull Double lat,
        @NotNull Double lng
) {
}
