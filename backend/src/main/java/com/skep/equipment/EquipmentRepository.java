package com.skep.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    List<Equipment> findBySupplierIdOrderByIdDesc(Long supplierId);
    List<Equipment> findAllByOrderByIdDesc();
    List<Equipment> findByCategoryOrderByIdDesc(EquipmentCategory category);
    List<Equipment> findBySupplierIdAndCategoryOrderByIdDesc(Long supplierId, EquipmentCategory category);
}
