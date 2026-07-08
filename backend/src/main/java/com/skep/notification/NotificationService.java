package com.skep.notification;

import com.skep.common.ApiException;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    /** 사용자 직접 알림 발신. */
    public void sendToUser(Long userId, String type, String title, String message,
                           String linkType, Long linkId, Long siteId) {
        save(Notification.builder()
                .targetUserId(userId)
                .type(type).title(title).message(message)
                .linkType(linkType).linkId(linkId).siteId(siteId)
                .build());
    }

    /** 회사 broadcast (target_user_id null). */
    public void sendToCompany(Long companyId, String type, String title, String message,
                              String linkType, Long linkId, Long siteId) {
        save(Notification.builder()
                .targetCompanyId(companyId)
                .type(type).title(title).message(message)
                .linkType(linkType).linkId(linkId).siteId(siteId)
                .build());
    }

    /** 시스템 broadcast (ADMIN 만 보임). */
    public void sendSystem(String type, String title, String message,
                           String linkType, Long linkId, Long siteId) {
        save(Notification.builder()
                .type(type).title(title).message(message)
                .linkType(linkType).linkId(linkId).siteId(siteId)
                .build());
    }

    private void save(Notification n) {
        try {
            repo.save(n);
        } catch (Exception e) {
            log.warn("notification save failed type={} title={}: {}", n.getType(), n.getTitle(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(AuthenticatedUser actor, int page, int size) {
        if (size < 1 || size > 100) size = 20;
        if (page < 0) page = 0;
        // ADMIN 은 전체. 회사 사용자는 직접 + 회사 broadcast.
        if (actor.role() == Role.ADMIN) {
            return repo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        }
        return repo.findVisibleFor(actor.id(), actor.companyId(), false,
                PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public long unreadCount(AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) {
            return repo.countByReadAtIsNull();
        }
        return repo.countUnread(actor.id(), actor.companyId(), false);
    }

    public int markAllRead(AuthenticatedUser actor) {
        var unread = repo.findUnreadFor(actor.id(), actor.companyId(),
                actor.role() == Role.ADMIN);
        int count = 0;
        for (Notification n : unread) {
            if (n.getReadAt() == null) { n.markRead(); count++; }
        }
        return count;
    }

    public Notification markRead(Long id, AuthenticatedUser actor) {
        Notification n = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다"));
        // 가시성 체크: 본인 직접 또는 같은 회사 broadcast 또는 ADMIN
        boolean visible = (n.getTargetUserId() != null && n.getTargetUserId().equals(actor.id()))
                || (n.getTargetUserId() == null && n.getTargetCompanyId() != null
                    && n.getTargetCompanyId().equals(actor.companyId()))
                || (actor.role() == Role.ADMIN);
        if (!visible) {
            throw ApiException.forbidden("NOTIFICATION_DENIED", "알림 접근 권한이 없습니다");
        }
        n.markRead();
        return n;
    }
}
