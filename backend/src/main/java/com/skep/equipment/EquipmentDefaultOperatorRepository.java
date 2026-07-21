package com.skep.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentDefaultOperatorRepository extends JpaRepository<EquipmentDefaultOperator, Long> {

    List<EquipmentDefaultOperator> findByEquipmentIdOrderByPriorityAsc(Long equipmentId);

    /** 장비 여러 대의 기본 조종원을 1쿼리로 — 서류수집 대상 다중선택(장비당 1회 호출 방지). */
    List<EquipmentDefaultOperator> findByEquipmentIdInOrderByEquipmentIdAscPriorityAsc(List<Long> equipmentIds);

    void deleteByEquipmentId(Long equipmentId);
}
