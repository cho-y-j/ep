package com.skep.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DailyEquipmentInspectionRepository extends JpaRepository<DailyEquipmentInspection, Long> {
    List<DailyEquipmentInspection> findByEquipmentIdOrderByInspectDateDescIdDesc(Long equipmentId);
}
