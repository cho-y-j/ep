package com.skep.field.dto;

import com.skep.site.Site;

/** 작업자 앱용 현장 요약 — 중심 좌표 + 지오펜스 반경. */
public record SiteSummary(
        Long id,
        String name,
        Double centerLat,
        Double centerLng,
        Integer radiusM
) {
    public static SiteSummary from(Site site) {
        return new SiteSummary(
                site.getId(),
                site.getName(),
                site.getLatitude(),
                site.getLongitude(),
                site.getGeofenceRadiusM()
        );
    }
}
