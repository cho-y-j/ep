package com.skep.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentDefaultOperatorRepository extends JpaRepository<EquipmentDefaultOperator, Long> {

    List<EquipmentDefaultOperator> findByEquipmentIdOrderByPriorityAsc(Long equipmentId);

    void deleteByEquipmentId(Long equipmentId);
}
