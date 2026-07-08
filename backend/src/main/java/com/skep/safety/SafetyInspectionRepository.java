package com.skep.safety;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SafetyInspectionRepository extends JpaRepository<SafetyInspection, Long> {

    List<SafetyInspection> findBySiteIdOrderByScheduledAtAsc(Long siteId);

    List<SafetyInspection> findBySupplierCompanyIdOrderByScheduledAtAsc(Long supplierCompanyId);

    List<SafetyInspection> findBySiteIdAndTargetTypeAndTargetId(Long siteId, InspectionTarget targetType, Long targetId);

    /** G-1 게이트: 작업 시작 시 자원별 COMPLETED 여부 확인용. */
    List<SafetyInspection> findBySiteIdAndStatus(Long siteId, InspectionStatus status);
}
