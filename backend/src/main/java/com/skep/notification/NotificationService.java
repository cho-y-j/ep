package com.skep.notification;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repo;
    private final CompanyRepository companyRepo;

    public NotificationService(NotificationRepository repo, CompanyRepository companyRepo) {
        this.repo = repo;
        this.companyRepo = companyRepo;
    }

    /**
     * P4d: actor 를 사람이 읽을 발신자 라벨로. ADMIN="관리자", 그 외="회사명 이름"(회사 미상 시 이름만).
     * 발송 지점에서 sender 인자로 넘겨 알림에 "누가 보냈는지" 남긴다.
     */
    @Transactional(readOnly = true)
    public String senderLabelOf(AuthenticatedUser actor) {
        if (actor == null) return null;
        if (actor.role() == Role.ADMIN) return "관리자";
        String name = actor.name() == null ? "" : actor.name().trim();
        if (actor.companyId() != null) {
            Company c = companyRepo.findById(actor.companyId()).orElse(null);
            if (c != null && c.getName() != null && !c.getName().isBlank()) {
                return name.isEmpty() ? c.getName() : c.getName() + " " + name;
            }
        }
        return name.isEmpty() ? null : name;
    }

    /** 사용자 직접 알림 발신. */
    public void sendToUser(Long userId, String type, String title, String message,
                           String linkType, Long linkId, Long siteId) {
        sendToUser(userId, type, title, message, linkType, linkId, siteId, null);
    }

    /** 사용자 직접 알림 발신 + 발신자 라벨. */
    public void sendToUser(Long userId, String type, String title, String message,
                           String linkType, Long linkId, Long siteId, String senderLabel) {
        save(Notification.builder()
                .targetUserId(userId)
                .type(type).title(title).message(message)
                .linkType(linkType).linkId(linkId).siteId(siteId)
                .senderLabel(senderLabel)
                .build());
    }

    /** 회사 broadcast (target_user_id null). */
    public void sendToCompany(Long companyId, String type, String title, String message,
                              String linkType, Long linkId, Long siteId) {
        sendToCompany(companyId, type, title, message, linkType, linkId, siteId, null);
    }

    /** 회사 broadcast + 발신자 라벨. */
    public void sendToCompany(Long companyId, String type, String title, String message,
                              String linkType, Long linkId, Long siteId, String senderLabel) {
        save(Notification.builder()
                .targetCompanyId(companyId)
                .type(type).title(title).message(message)
                .linkType(linkType).linkId(linkId).siteId(siteId)
                .senderLabel(senderLabel)
                .build());
    }

    /** 시스템 broadcast (ADMIN 만 보임). */
    public void sendSystem(String type, String title, String message,
                           String linkType, Long linkId, Long siteId) {
        save(Notification.builder()
                .type(type).title(title).message(message)
                .linkType(linkType).linkId(linkId).siteId(siteId)
                .senderLabel("시스템")
                .build());
    }

    private void save(Notification n) {
        try {
            repo.save(n);
        } catch (Exception e) {
            log.warn("notification save failed type={} title={}: {}", n.getType(), n.getTitle(), e.getMessage());
        }
    }

    /**
     * P4d: 필터 지원 목록. ADMIN 은 전체, 회사 사용자는 직접 + 회사 broadcast(시스템 broadcast 제외).
     * unread(null=전체), types(null/빈=전체), fromDate(null=무제한), q(제목·내용·발신자 부분일치)는 모두 선택.
     */
    @Transactional(readOnly = true)
    public Page<Notification> list(AuthenticatedUser actor, int page, int size,
                                   Boolean unread, List<String> types, LocalDate fromDate, String q) {
        if (size < 1 || size > 100) size = 20;
        if (page < 0) page = 0;
        boolean hasUnread = unread != null;
        boolean unreadVal = Boolean.TRUE.equals(unread);
        boolean hasTypes = types != null && !types.isEmpty();
        List<String> typeList = hasTypes ? types : List.of("");
        boolean hasFrom = fromDate != null;
        LocalDateTime fromTs = hasFrom ? fromDate.atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
        String qTrim = (q != null && !q.isBlank()) ? "%" + q.trim().toLowerCase() + "%" : null;
        boolean hasQ = qTrim != null;
        String qLike = hasQ ? qTrim : "";
        return repo.findFiltered(
                actor.role() == Role.ADMIN, actor.id(), actor.companyId(),
                hasUnread, unreadVal, hasTypes, typeList, hasFrom, fromTs, hasQ, qLike,
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
