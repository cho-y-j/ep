package com.skep.onboarding.dto;

import com.skep.document.OwnerType;
import com.skep.onboarding.OnboardingMode;
import com.skep.onboarding.SiteResourceOnboarding;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record OnboardingResponse(
        Long id,
        Long supplierCompanyId,
        String supplierCompanyName,
        Long siteId,
        String siteName,
        Long bpCompanyId,
        String bpCompanyName,
        OwnerType ownerType,
        Long ownerId,
        String ownerLabel,
        LocalDate inspectionDate,
        LocalDate educationDate,
        LocalDate healthDate,
        OnboardingMode mode,
        String verbalApprover,
        LocalDateTime verbalAt,
        String memo,
        LocalDateTime requestedAt,
        LocalDateTime approvedAt
) {
    public static OnboardingResponse from(SiteResourceOnboarding o, String supplierName,
                                          String bpName, String ownerLabel) {
        return new OnboardingResponse(
                o.getId(), o.getSupplierCompanyId(), supplierName,
                o.getSiteId(), o.getSiteName(),
                o.getBpCompanyId(), bpName,
                o.getOwnerType(), o.getOwnerId(), ownerLabel,
                o.getInspectionDate(), o.getEducationDate(), o.getHealthDate(),
                o.getMode(), o.getVerbalApprover(), o.getVerbalAt(),
                o.getMemo(), o.getRequestedAt(), o.getApprovedAt()
        );
    }
}
