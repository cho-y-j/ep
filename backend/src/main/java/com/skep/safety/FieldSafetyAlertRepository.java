package com.skep.safety;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    /** P5-W1 중복 발화 방지 — 해당 작업자의 활성(미해결) vital 경보 존재 여부. */
    boolean existsByPersonIdAndKindInAndResolvedFalse(Long personId, Collection<String> kinds);

    /** P5-W4 과로 경고 인당 일 1회 가드 — 오늘 이미 발화한 kind 경보 존재 여부. */
    boolean existsByPersonIdAndKindAndCreatedAtAfter(Long personId, String kind, LocalDateTime after);

    /** P5-W3 릴레이 수신 — 피재자의 활성 EMERGENCY 경보(있으면 위치 보강, 없으면 신규 생성). */
    Optional<FieldSafetyAlert> findFirstByPersonIdAndSeverityAndResolvedFalseOrderByCreatedAtDesc(
            Long personId, String severity);

    /** P5-W3 릴레이 dedupe(victim당 5분) — 최근 릴레이가 기록된 경보가 있으면 중복 처리 방지. */
    boolean existsByPersonIdAndKindAndRelayedAtAfter(Long personId, String kind, LocalDateTime after);

    /** P5-W2 60초 무응답 확대 후보 — 대응체인 발동(peer_notified)됐고 응답·확대·해결 전인 경보. */
    List<FieldSafetyAlert> findByPeerNotifiedAtIsNotNullAndFirstResponseAtIsNullAndPeerEscalatedAtIsNullAndResolvedFalse();
}
