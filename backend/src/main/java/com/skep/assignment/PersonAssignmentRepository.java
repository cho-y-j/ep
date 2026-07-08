package com.skep.assignment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PersonAssignmentRepository extends JpaRepository<PersonAssignment, Long> {

    Optional<PersonAssignment> findByPersonIdAndReleasedAtIsNull(Long personId);

    List<PersonAssignment> findByPersonIdOrderByAssignedAtDesc(Long personId);

    List<PersonAssignment> findBySiteIdOrderByAssignedAtDesc(Long siteId);

    List<PersonAssignment> findBySiteIdAndReleasedAtIsNullOrderByAssignedAtDesc(Long siteId);

    boolean existsByPersonIdAndSiteId(Long personId, Long siteId);

    @Query("""
            SELECT DISTINCT a.personId FROM PersonAssignment a
            WHERE a.siteId = :siteId AND a.personId IN :personIds
            """)
    List<Long> findPersonIdsPreviouslyOnSite(@Param("siteId") Long siteId,
                                             @Param("personIds") List<Long> personIds);

    /** S-8.5: 특정 작업계획서가 자동 생성한 활성 배치들. */
    List<PersonAssignment> findByTriggeredByWorkPlanIdAndReleasedAtIsNull(Long workPlanId);
}
