package com.skep.site.dto;

import jakarta.validation.constraints.NotNull;

public record AddSiteParticipantRequest(
        @NotNull Long companyId
) {
}
