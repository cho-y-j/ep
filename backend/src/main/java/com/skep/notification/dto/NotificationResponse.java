package com.skep.notification.dto;

import com.skep.notification.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Long targetUserId,
        Long targetCompanyId,
        Long siteId,
        String type,
        String title,
        String message,
        String linkType,
        Long linkId,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getTargetUserId(), n.getTargetCompanyId(),
                n.getSiteId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getLinkType(), n.getLinkId(), n.getReadAt(), n.getCreatedAt()
        );
    }
}
