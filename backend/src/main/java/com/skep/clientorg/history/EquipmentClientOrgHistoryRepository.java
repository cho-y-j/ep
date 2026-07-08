package com.skep.clientorg.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EquipmentClientOrgHistoryRepository extends JpaRepository<EquipmentClientOrgHistory, Long> {

    /** 특정 장비의 모든 이력 (최신순). */
    List<EquipmentClientOrgHistory> findByEquipmentIdOrderByPeriodStartDesc(Long equipmentId);

    /** 여러 장비의 이력을 한 번에 — 후보 카드 chip 채울 때 N+1 회피. */
    List<EquipmentClientOrgHistory> findByEquipmentIdIn(Collection<Long> equipmentIds);

    /** 장비가 특정 ClientOrg 경험 있는지 — 정렬 가중치. */
    long countByEquipmentIdAndClientOrgId(Long equipmentId, Long clientOrgId);

    /** ClientOrg 삭제 가드 — 참조 이력 수. */
    long countByClientOrgId(Long clientOrgId);

    /** 자동 추가 중복 방지 — 같은 (equipment, clientOrg, workPlanId) 이미 있나. */
    @Query("""
            SELECT COUNT(h) FROM EquipmentClientOrgHistory h
            WHERE h.equipmentId = :equipmentId AND h.clientOrgId = :clientOrgId
              AND h.source = com.skep.clientorg.history.HistorySource.WORK_PLAN
              AND h.sourceRefId = :workPlanId
            """)
    long countWorkPlanSource(@Param("equipmentId") Long equipmentId,
                              @Param("clientOrgId") Long clientOrgId,
                              @Param("workPlanId") Long workPlanId);
}
