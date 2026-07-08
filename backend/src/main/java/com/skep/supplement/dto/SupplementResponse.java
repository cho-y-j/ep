package com.skep.supplement.dto;

import com.skep.document.OwnerType;
import com.skep.supplement.DocumentSupplementStatus;
import com.skep.user.Role;

import java.time.LocalDateTime;

public record SupplementResponse(
        Long id,
        Long requesterUserId,
        String requesterUserName,
        Role requesterRole,
        Long targetSupplierCompanyId,
        String targetSupplierCompanyName,
        OwnerType targetOwnerType,
        Long targetOwnerId,
        String targetOwnerName,
        Long documentTypeId,
        String documentTypeName,
        Long contextSiteId,
        String contextSiteName,
        Long contextWorkPlanId,
        String reason,
        DocumentSupplementStatus status,
        Long resolvedDocId,
        LocalDateTime resolvedAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt
) {}
