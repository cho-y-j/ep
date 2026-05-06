package com.skep.equipment.timeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentLocationRepository extends JpaRepository<EquipmentLocation, Long> {
    List<EquipmentLocation> findByEquipmentIdOrderByRecordedAtDesc(Long equipmentId);
}
