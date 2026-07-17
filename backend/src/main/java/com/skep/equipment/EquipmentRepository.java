package com.skep.equipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    Optional<Equipment> findByNfcTagId(String nfcTagId);

    @Query("SELECT e FROM Equipment e WHERE e.inspectionDueDate IS NOT NULL OR e.oilChangeDueDate IS NOT NULL OR e.registrationExpiry IS NOT NULL")
    List<Equipment> findWithAnyDueDate();
    List<Equipment> findBySupplierIdOrderByIdDesc(Long supplierId);
    List<Equipment> findAllByOrderByIdDesc();
    long countBySupplierId(Long supplierId);
    List<Equipment> findByCategoryOrderByIdDesc(String category);
    List<Equipment> findBySupplierIdAndCategoryOrderByIdDesc(Long supplierId, String category);
    /** 후보 조회: 여러 공급사 자원 일괄 조회. */
    List<Equipment> findBySupplierIdInOrderByIdDesc(Collection<Long> supplierIds);
}
