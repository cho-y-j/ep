package com.skep.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentDefaultOperatorRepository extends JpaRepository<EquipmentDefaultOperator, Long> {

    List<EquipmentDefaultOperator> findByEquipmentIdOrderByPriorityAsc(Long equipmentId);

    /** 장비 여러 대의 기본 조종원을 1쿼리로 — 서류수집 대상 다중선택(장비당 1회 호출 방지). */
    List<EquipmentDefaultOperator> findByEquipmentIdInOrderByEquipmentIdAscPriorityAsc(List<Long> equipmentIds);

    /** R1 역방향 — 이 인원이 조합(교대조)으로 묶인 장비들(세트 허브 양방향: 인력 상세→매칭 장비). */
    List<EquipmentDefaultOperator> findByPersonId(Long personId);

    void deleteByEquipmentId(Long equipmentId);
}
