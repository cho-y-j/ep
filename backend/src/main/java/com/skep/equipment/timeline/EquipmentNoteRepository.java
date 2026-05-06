package com.skep.equipment.timeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentNoteRepository extends JpaRepository<EquipmentNote, Long> {
    List<EquipmentNote> findByEquipmentIdOrderByCreatedAtDesc(Long equipmentId);
}
