package com.skep.legalinspection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LegalInspectionRepository extends JpaRepository<LegalInspection, Long> {

    Optional<LegalInspection> findByEquipmentIdAndInspectDateAndTemplateId(
            Long equipmentId, LocalDate inspectDate, Long templateId);

    /** 오늘 완료 배지 — 여러 장비의 특정일 법정점검. */
    List<LegalInspection> findByEquipmentIdInAndInspectDate(
            Collection<Long> equipmentIds, LocalDate inspectDate);

    /** P3d 이행 보고서 — 현장·기간 배치 조회(N+1 회피). */
    List<LegalInspection> findBySiteIdAndInspectDateBetweenOrderByInspectDateAsc(
            Long siteId, LocalDate from, LocalDate to);
}
