package com.skep.site.dto;

import com.skep.company.Company;
import com.skep.company.CompanyType;
import com.skep.site.SiteParticipant;
import com.skep.site.SiteParticipantStatus;
import com.skep.site.SiteParticipantType;

import java.time.LocalDateTime;

public record SiteParticipantResponse(
        Long id,
        Long siteId,
        Long companyId,
        String companyName,
        CompanyType companyType,
        SiteParticipantType participantType,
        SiteParticipantStatus status,
        LocalDateTime addedAt
) {
    public static SiteParticipantResponse from(SiteParticipant participant, Company company) {
        return new SiteParticipantResponse(
                participant.getId(),
                participant.getSiteId(),
                participant.getCompanyId(),
                company != null ? company.getName() : null,
                company != null ? company.getType() : null,
                participant.getParticipantType(),
                participant.getStatus(),
                participant.getAddedAt()
        );
    }
}
