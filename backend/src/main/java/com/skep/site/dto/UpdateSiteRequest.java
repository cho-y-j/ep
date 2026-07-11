package com.skep.site.dto;

import com.skep.site.SiteStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateSiteRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 64) String code,
        @Size(max = 255) String address,
        @Size(max = 255) String detailAddress,
        LocalDate startDate,
        LocalDate endDate,
        SiteStatus status,
        Double latitude,
        Double longitude,
        String polygonGeojson,
        Integer mapZoom,
        @Min(1) @Max(31) Integer settlementDay
) {
}
