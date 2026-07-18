package com.skep.announcement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnnouncementRecipientRepository extends JpaRepository<AnnouncementRecipient, Long> {

    List<AnnouncementRecipient> findByAnnouncementIdOrderByIdAsc(Long announcementId);

    /** 여러 공지의 수신자 배치 조회(확인율 집계 N+1 회피). */
    List<AnnouncementRecipient> findByAnnouncementIdIn(Collection<Long> announcementIds);

    /** 작업자 본인 수신 목록 — 최신 공지 우선(announcement_id desc). */
    List<AnnouncementRecipient> findByPersonIdOrderByAnnouncementIdDesc(Long personId);

    Optional<AnnouncementRecipient> findByAnnouncementIdAndPersonId(Long announcementId, Long personId);
}
