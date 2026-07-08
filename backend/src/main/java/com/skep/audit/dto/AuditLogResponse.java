package com.skep.audit.dto;

import com.skep.audit.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long actorUserId,
        String actorRole,
        Long actorCompanyId,
        String action,
        String targetType,
        Long targetId,
        Long targetCompanyId,
        Long siteId,
        String beforeJson,
        String afterJson,
        LocalDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorUserId(),
                log.getActorRole(),
                log.getActorCompanyId(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getTargetCompanyId(),
                log.getSiteId(),
                log.getBeforeJson(),
                log.getAfterJson(),
                log.getCreatedAt()
        );
    }
}
