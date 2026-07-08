package com.skep.safety;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FieldSafetyAlertRepository extends JpaRepository<FieldSafetyAlert, Long> {

    List<FieldSafetyAlert> findByPersonIdOrderByCreatedAtDesc(Long personId);

    List<FieldSafetyAlert> findBySiteIdOrderByCreatedAtDesc(Long siteId);

    List<FieldSafetyAlert> findByBpCompanyIdOrderByCreatedAtDesc(Long bpCompanyId);

    List<FieldSafetyAlert> findByResolvedFalseOrderByCreatedAtDesc();

    /** ADMIN 전체 목록 — 최신 100건 (무순서 findAll + limit 의 임의 절단 방지). */
    List<FieldSafetyAlert> findTop100ByOrderByCreatedAtDesc();

    List<FieldSafetyAlert> findByPersonIdAndCreatedAtAfterOrderByCreatedAtDesc(Long personId, LocalDateTime since);
}
