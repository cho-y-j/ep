package com.skep.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DailyEquipmentInspectionRepository extends JpaRepository<DailyEquipmentInspection, Long> {
    List<DailyEquipmentInspection> findByEquipmentIdOrderByInspectDateDescIdDesc(Long equipmentId);

    /** 원청 관제 — 현장 장비들의 특정일(오늘) 조종원 일일점검 완료 여부 집계. */
    List<DailyEquipmentInspection> findByEquipmentIdInAndInspectDate(
            java.util.Collection<Long> equipmentIds, java.time.LocalDate inspectDate);

    /** P3d 이행 보고서 — 현장 장비들의 기간 조종원 일일점검 배치 조회(N+1 회피). */
    List<DailyEquipmentInspection> findByEquipmentIdInAndInspectDateBetween(
            java.util.Collection<Long> equipmentIds, java.time.LocalDate from, java.time.LocalDate to);
}
