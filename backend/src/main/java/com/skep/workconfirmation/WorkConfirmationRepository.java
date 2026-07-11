package com.skep.workconfirmation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
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

    // 정산 근무일수 자동 파생 — 대상 인원들의 확인서를 기간(min~max)으로 한 번에 조회. 상태 필터는 서비스에서.
    List<WorkConfirmation> findByPersonIdInAndWorkDateBetween(Collection<Long> personIds, LocalDate from, LocalDate to);

    // 자원 파이프라인 — 인력들의 확인서 일괄 조회(최근 workDate 집계용, N+1 회피).
    List<WorkConfirmation> findByPersonIdIn(Collection<Long> personIds);
}
