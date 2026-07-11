package com.skep.safety;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SafetyInspectionRepository extends JpaRepository<SafetyInspection, Long> {

    List<SafetyInspection> findBySiteIdOrderByScheduledAtAsc(Long siteId);

    List<SafetyInspection> findBySupplierCompanyIdOrderByScheduledAtAsc(Long supplierCompanyId);

    List<SafetyInspection> findBySiteIdAndTargetTypeAndTargetId(Long siteId, InspectionTarget targetType, Long targetId);

    /** 투입 준비 가시성 — 여러 자원의 검사 일괄 조회(현장 무관, N+1 회피). */
    List<SafetyInspection> findByTargetTypeAndTargetIdIn(InspectionTarget targetType, Collection<Long> targetIds);

    /** G-1 게이트: 작업 시작 시 자원별 COMPLETED 여부 확인용. */
    List<SafetyInspection> findBySiteIdAndStatus(Long siteId, InspectionStatus status);
}
