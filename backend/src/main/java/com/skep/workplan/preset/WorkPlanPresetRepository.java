package com.skep.workplan.preset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkPlanPresetRepository extends JpaRepository<WorkPlanPreset, Long> {
    List<WorkPlanPreset> findByUserIdOrderBySlotAsc(Long userId);
    Optional<WorkPlanPreset> findByUserIdAndSlot(Long userId, Short slot);
}
