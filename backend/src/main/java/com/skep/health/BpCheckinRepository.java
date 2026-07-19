package com.skep.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BpCheckinRepository extends JpaRepository<BpCheckin, Long> {

    /** 현장·날짜 체크인 목록(측정 시각 최신순) — 관제 오늘 현황. */
    List<BpCheckin> findBySiteIdAndMeasuredAtBetweenOrderByMeasuredAtDesc(
            Long siteId, LocalDateTime from, LocalDateTime to);

    /** 오늘 이미 측정한 인원 판별 — 지정 인원 중 기간 내 체크인. */
    List<BpCheckin> findByPersonIdInAndMeasuredAtBetween(
            Collection<Long> personIds, LocalDateTime from, LocalDateTime to);
}
