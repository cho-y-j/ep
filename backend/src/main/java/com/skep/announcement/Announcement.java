package com.skep.announcement;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 발송된 공지 — 확인(읽음) 추적용 영속 기록. FCM 발송(fire-and-forget)과 별개로,
 * 수신자별 확인 시각(AnnouncementRecipient)을 남겨 발송자에게 확인율·미확인자 명단을 제공한다.
 */
@Entity
@Table(name = "announcements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 현장 스코프(선택). NULL=현장 미지정. */
    @Column(name = "site_id")
    private Long siteId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "sender_user_id")
    private Long senderUserId;

    @Column(name = "sender_company_id")
    private Long senderCompanyId;

    @Column(length = 16)
    private String target;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Announcement(Long siteId, String title, String body,
                         Long senderUserId, Long senderCompanyId, String target) {
        this.siteId = siteId;
        this.title = title;
        this.body = body;
        this.senderUserId = senderUserId;
        this.senderCompanyId = senderCompanyId;
        this.target = target;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
