package com.skep.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentTypeRepository extends JpaRepository<EquipmentType, String> {

    /** 활성 종류만, 정렬 순. FE 드롭다운·라벨 소스. */
    List<EquipmentType> findByActiveTrueOrderBySortOrderAsc();

    /** 전체(비활성 포함), 정렬 순. 어드민 목록. */
    List<EquipmentType> findAllByOrderBySortOrderAsc();
}
