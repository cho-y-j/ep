package com.skep.notification;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 알림. audit_logs 와 분리.
 *
 * 발신 정책:
 *  - target_user_id 있음 → 그 사용자에게 직접
 *  - target_company_id 있음 (target_user_id null) → 그 회사 모든 (회사 관리자 우선) 사용자에게 보임 — Service.list 에서 처리
 *  - 둘 다 없음 → 시스템 broadcast (ADMIN 만 보이게)
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "target_company_id")
    private Long targetCompanyId;

    @Column(name = "site_id")
    private Long siteId;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "link_type", length = 32)
    private String linkType;

    @Column(name = "link_id")
    private Long linkId;

    /** P4d: 발신자 표시용 라벨. 예 "시스템 (강풍 경보)"·"테스트 BP건설(주) 김소장"·"관리자". 기존 행은 NULL. */
    @Column(name = "sender_label", length = 120)
    private String senderLabel;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Notification(Long targetUserId, Long targetCompanyId, Long siteId,
                         String type, String title, String message,
                         String linkType, Long linkId, String senderLabel) {
        this.targetUserId = targetUserId;
        this.targetCompanyId = targetCompanyId;
        this.siteId = siteId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.linkType = linkType;
        this.linkId = linkId;
        this.senderLabel = senderLabel;
    }

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    public void markRead() {
        if (this.readAt == null) this.readAt = LocalDateTime.now();
    }
}
