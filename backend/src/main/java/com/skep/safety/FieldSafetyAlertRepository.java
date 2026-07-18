package com.skep.safety;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface FieldSafetyAlertRepository extends JpaRepository<FieldSafetyAlert, Long> {

    /**
     * S5' 에스컬레이션 후보 — ack 필요 등급(severity 지정)·미확인·미에스컬레이션·미해결·생성 cutoff 이전.
     * kind 최종 필터(작업자 수신 알림만)는 스케줄러의 순수 판정에서 수행.
     */
    List<FieldSafetyAlert> findBySeverityInAndAcknowledgedAtIsNullAndEscalatedAtIsNullAndResolvedFalseAndCreatedAtBefore(
            Collection<String> severities, LocalDateTime cutoff);

    List<FieldSafetyAlert> findByPersonIdOrderByCreatedAtDesc(Long personId);

    List<FieldSafetyAlert> findBySiteIdOrderByCreatedAtDesc(Long siteId);

    /** P3d 이행 보고서 — 현장·기간 배치 조회(N+1 회피). created_at 오름차순(타임라인). */
    List<FieldSafetyAlert> findBySiteIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long siteId, LocalDateTime from, LocalDateTime to);

    List<FieldSafetyAlert> findByBpCompanyIdOrderByCreatedAtDesc(Long bpCompanyId);

    List<FieldSafetyAlert> findByResolvedFalseOrderByCreatedAtDesc();

    /** ADMIN 전체 목록 — 최신 100건 (무순서 findAll + limit 의 임의 절단 방지). */
    List<FieldSafetyAlert> findTop100ByOrderByCreatedAtDesc();

    List<FieldSafetyAlert> findByPersonIdAndCreatedAtAfterOrderByCreatedAtDesc(Long personId, LocalDateTime since);
}
