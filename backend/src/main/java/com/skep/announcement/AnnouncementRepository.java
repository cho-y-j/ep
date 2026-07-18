package com.skep.announcement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /** 발송자(BP) 회사 공지 — 최신순. */
    List<Announcement> findBySenderCompanyIdOrderByCreatedAtDesc(Long senderCompanyId);

    /** 현장 스코프 공지 — 상황판 [공지] 탭·요약. 최신순. */
    List<Announcement> findBySiteIdOrderByCreatedAtDesc(Long siteId);

    /** 여러 announcement 확인 집계용 배치 조회. */
    List<Announcement> findByIdInOrderByCreatedAtDesc(Collection<Long> ids);
}
