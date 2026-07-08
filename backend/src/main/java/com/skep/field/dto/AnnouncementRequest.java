package com.skep.field.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnnouncementRequest(
        Long siteId,
        @NotBlank @Size(max = 150) String title,
        @NotBlank String body
) {
}
