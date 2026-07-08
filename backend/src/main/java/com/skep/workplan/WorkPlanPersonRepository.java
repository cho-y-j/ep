package com.skep.workplan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkPlanPersonRepository extends JpaRepository<WorkPlanPerson, Long> {
    List<WorkPlanPerson> findByWorkPlanIdOrderByIdAsc(Long workPlanId);
    Optional<WorkPlanPerson> findByWorkPlanIdAndPersonId(Long workPlanId, Long personId);
    Optional<WorkPlanPerson> findByWorkPlanIdAndPersonIdAndRole(Long workPlanId, Long personId, String role);
    void deleteByWorkPlanIdAndPersonId(Long workPlanId, Long personId);

    /** 대시보드용 — work_plan_id 들의 모든 인원 행. */
    List<WorkPlanPerson> findByWorkPlanIdIn(java.util.Collection<Long> workPlanIds);

    /** P1: 공급사가 해당 작업계획서에 참여 중인지 검증용. */
    boolean existsByWorkPlanIdAndSupplierCompanyId(Long workPlanId, Long supplierCompanyId);

    List<WorkPlanPerson> findByPersonId(Long personId);
}
