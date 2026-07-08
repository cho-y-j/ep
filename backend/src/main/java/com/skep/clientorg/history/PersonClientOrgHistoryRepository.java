package com.skep.clientorg.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PersonClientOrgHistoryRepository extends JpaRepository<PersonClientOrgHistory, Long> {

    List<PersonClientOrgHistory> findByPersonIdOrderByPeriodStartDesc(Long personId);

    List<PersonClientOrgHistory> findByPersonIdIn(Collection<Long> personIds);

    long countByPersonIdAndClientOrgId(Long personId, Long clientOrgId);

    /** ClientOrg 삭제 가드 — 참조 이력 수. */
    long countByClientOrgId(Long clientOrgId);

    @Query("""
            SELECT COUNT(h) FROM PersonClientOrgHistory h
            WHERE h.personId = :personId AND h.clientOrgId = :clientOrgId
              AND h.source = com.skep.clientorg.history.HistorySource.WORK_PLAN
              AND h.sourceRefId = :workPlanId
            """)
    long countWorkPlanSource(@Param("personId") Long personId,
                              @Param("clientOrgId") Long clientOrgId,
                              @Param("workPlanId") Long workPlanId);
}
