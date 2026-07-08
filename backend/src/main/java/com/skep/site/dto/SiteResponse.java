package com.skep.site.dto;

import com.skep.company.Company;
import com.skep.site.Site;
import com.skep.site.SiteStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SiteResponse(
        Long id,
        Long bpCompanyId,
        String bpCompanyName,
        String name,
        String code,
        String address,
        String detailAddress,
        LocalDate startDate,
        LocalDate endDate,
        SiteStatus status,
        Double latitude,
        Double longitude,
        String polygonGeojson,
        Integer mapZoom,
        int participantCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<SiteParticipantResponse> participants
) {
    public static SiteResponse summary(Site site, Company bpCompany, int participantCount) {
        return from(site, bpCompany, participantCount, null);
    }

    public static SiteResponse detail(Site site, Company bpCompany, List<SiteParticipantResponse> participants) {
        return from(site, bpCompany, participants.size(), participants);
    }

    private static SiteResponse from(Site site, Company bpCompany, int participantCount,
                                     List<SiteParticipantResponse> participants) {
        return new SiteResponse(
                site.getId(),
                site.getBpCompanyId(),
                bpCompany != null ? bpCompany.getName() : null,
                site.getName(),
                site.getCode(),
                site.getAddress(),
                site.getDetailAddress(),
                site.getStartDate(),
                site.getEndDate(),
                site.getStatus(),
                site.getLatitude(),
                site.getLongitude(),
                site.getPolygonGeojson(),
                site.getMapZoom(),
                participantCount,
                site.getCreatedAt(),
                site.getUpdatedAt(),
                participants
        );
    }
}
