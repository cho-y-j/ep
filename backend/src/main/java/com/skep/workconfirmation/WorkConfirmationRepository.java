package com.skep.workconfirmation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkConfirmationRepository extends JpaRepository<WorkConfirmation, Long> {

    List<WorkConfirmation> findByWorkPlanIdOrderByWorkDateDescIdDesc(Long workPlanId);

    Optional<WorkConfirmation> findByWorkPlanIdAndPersonId(Long workPlanId, Long personId);

    List<WorkConfirmation> findByIssuingSupplierCompanyIdOrderByWorkDateDescIdDesc(Long supplierCompanyId);

    List<WorkConfirmation> findByBpCompanyIdOrderByWorkDateDescIdDesc(Long bpCompanyId);

    List<WorkConfirmation> findByPersonIdOrderByWorkDateDescIdDesc(Long personId);

    // 월별 집계 — workDate 범위 (양끝 포함). 역할별 스코프.
    List<WorkConfirmation> findByWorkDateBetween(LocalDate start, LocalDate end);

    List<WorkConfirmation> findByBpCompanyIdAndWorkDateBetween(Long bpCompanyId, LocalDate start, LocalDate end);

    List<WorkConfirmation> findByIssuingSupplierCompanyIdAndWorkDateBetween(Long supplierCompanyId, LocalDate start, LocalDate end);
}
