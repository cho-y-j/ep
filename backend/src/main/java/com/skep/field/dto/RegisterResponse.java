package com.skep.field.dto;

public record RegisterResponse(
        Long workerId,
        String token,
        SiteSummary site
) {
}
