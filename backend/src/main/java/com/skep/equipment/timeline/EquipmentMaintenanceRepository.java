package com.skep.equipment.timeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentMaintenanceRepository extends JpaRepository<EquipmentMaintenance, Long> {
    List<EquipmentMaintenance> findByEquipmentIdOrderByMaintainedAtDesc(Long equipmentId);
}
