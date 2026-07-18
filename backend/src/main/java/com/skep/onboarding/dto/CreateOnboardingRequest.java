package com.skep.onboarding.dto;

import com.skep.document.OwnerType;
import com.skep.onboarding.OnboardingMode;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 기투입 등록(공급사). 자원 1건씩 — 프론트가 다중선택을 자원별로 반복 호출.
 * mode 는 REQUESTED(BP 승인요청) 또는 VERBAL(구두승인 즉시확정)만.
 */
public record CreateOnboardingRequest(
        @NotNull OwnerType ownerType,
        @NotNull Long ownerId,
        Long siteId,
        String siteName,
        Long bpCompanyId,
        LocalDate inspectionDate,
        LocalDate educationDate,
        LocalDate healthDate,
        @NotNull OnboardingMode mode,
        String verbalApprover,
        String memo
) {}
