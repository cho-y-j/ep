package com.skep.equipment.timeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentInspectionRepository extends JpaRepository<EquipmentInspection, Long> {
    List<EquipmentInspection> findByEquipmentIdOrderByInspectedAtDesc(Long equipmentId);
}
