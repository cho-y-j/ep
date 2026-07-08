package com.skep.workplan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkPlanComplianceCheckRepository extends JpaRepository<WorkPlanComplianceCheck, Long> {
    List<WorkPlanComplianceCheck> findByWorkPlanIdOrderByCheckedAtDesc(Long workPlanId);
}
