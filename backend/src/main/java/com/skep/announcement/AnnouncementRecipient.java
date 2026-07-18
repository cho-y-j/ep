package com.skep.announcement;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 공지 수신자별 확인(읽음) 기록. read_at NULL=미확인. */
@Entity
@Table(name = "announcement_recipients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnouncementRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "announcement_id", nullable = false)
    private Long announcementId;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    private AnnouncementRecipient(Long announcementId, Long personId) {
        this.announcementId = announcementId;
        this.personId = personId;
    }

    /** 확인(읽음) 처리 — 최초 1회만 시각 기록(멱등). */
    public void markRead() {
        if (this.readAt == null) this.readAt = LocalDateTime.now();
    }
}
