package com.skep.safety;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SafetyAlertResponseRepository extends JpaRepository<SafetyAlertResponse, Long> {

    /** 멱등 저장·중복 탭 판정 — 이 동료가 이 경보에 이미 응답했는지. */
    Optional<SafetyAlertResponse> findByAlertIdAndPersonId(Long alertId, Long personId);

    /** 경보 1건의 응답자(관제 상세 "N명 응답"). 응답 시각 오름차순(최초 응답자 먼저). */
    List<SafetyAlertResponse> findByAlertIdOrderByCreatedAtAsc(Long alertId);

    /** 목록 직렬화 배치 조회(N+1 회피). */
    List<SafetyAlertResponse> findByAlertIdInOrderByCreatedAtAsc(Collection<Long> alertIds);
}
