package com.skep.assignment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EquipmentAssignmentRepository extends JpaRepository<EquipmentAssignment, Long> {

    /** 자원의 활성(미해제) 배치 조회. */
    Optional<EquipmentAssignment> findByEquipmentIdAndReleasedAtIsNull(Long equipmentId);

    /** 자원의 모든 이력 (최신순). */
    List<EquipmentAssignment> findByEquipmentIdOrderByAssignedAtDesc(Long equipmentId);

    /** 현장의 모든 이력 (최신순). */
    List<EquipmentAssignment> findBySiteIdOrderByAssignedAtDesc(Long siteId);

    /** 현장의 활성(미해제) 배치 자원들. */
    List<EquipmentAssignment> findBySiteIdAndReleasedAtIsNullOrderByAssignedAtDesc(Long siteId);

    /** 자원이 특정 현장에 과거 배치된 적이 있는지 (현재 활성/해제 무관). */
    boolean existsByEquipmentIdAndSiteId(Long equipmentId, Long siteId);

    /** 자원 리스트가 특정 현장에 과거 배치된 적 있는 자원 ID들. 후보 추천에 사용. */
    @Query("""
            SELECT DISTINCT a.equipmentId FROM EquipmentAssignment a
            WHERE a.siteId = :siteId AND a.equipmentId IN :equipmentIds
            """)
    List<Long> findEquipmentIdsPreviouslyOnSite(@Param("siteId") Long siteId,
                                                @Param("equipmentIds") List<Long> equipmentIds);

    /** S-8.5: 특정 작업계획서가 자동 생성한 활성 배치들 (complete/cancel 시 자동 해제용). */
    List<EquipmentAssignment> findByTriggeredByWorkPlanIdAndReleasedAtIsNull(Long workPlanId);
}
