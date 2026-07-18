package com.skep.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateSiteRequest(
        Long bpCompanyId,
        Long clientOrgId,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 64) String code,
        @Size(max = 255) String address,
        @Size(max = 255) String detailAddress,
        LocalDate startDate,
        LocalDate endDate,
        Double latitude,
        Double longitude,
        String polygonGeojson,
        Integer mapZoom
) {
}
