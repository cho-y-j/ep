package com.skep.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentTypeDocRequirementRepository
        extends JpaRepository<EquipmentTypeDocRequirement, EquipmentTypeDocRequirementId> {

    List<EquipmentTypeDocRequirement> findByEquipmentTypeCode(String equipmentTypeCode);

    List<EquipmentTypeDocRequirement> findByEquipmentTypeCodeAndRequiredTrue(String equipmentTypeCode);
}
