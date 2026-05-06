package com.skep.equipment.timeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentOperationRepository extends JpaRepository<EquipmentOperation, Long> {
    List<EquipmentOperation> findByEquipmentIdOrderByStartedAtDesc(Long equipmentId);
}
