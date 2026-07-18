package com.skep.dailywork;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface DailyWorkLogRepository extends JpaRepository<DailyWorkLog, Long> {

    /** 정산 OT 5분류 — 여러 장비의 일일 확인서 일괄(N+1 회피). 기간·서명 필터는 서비스에서. */
    List<DailyWorkLog> findByEquipmentIdIn(Collection<Long> equipmentIds);

    /** 정산 OT 5분류 — 여러 인원의 일일 확인서 일괄(N+1 회피). */
    List<DailyWorkLog> findByPersonIdIn(Collection<Long> personIds);

    /** 공급사 자기 회사 일일 확인서(최근순). */
    List<DailyWorkLog> findBySupplierCompanyIdOrderByWorkDateDescIdDesc(Long supplierCompanyId);

    /** BP 자기 앞 일일 확인서(서명 대상 포함, 최근순). */
    List<DailyWorkLog> findByBpCompanyIdOrderByWorkDateDescIdDesc(Long bpCompanyId);

    /** 작업자(운전원) 본인 일일 확인서(최근순). */
    List<DailyWorkLog> findByPersonIdOrderByWorkDateDescIdDesc(Long personId);

    /** 중복 방지 — 같은 공급사·같은 날·같은 장비. */
    boolean existsBySupplierCompanyIdAndWorkDateAndEquipmentId(Long supplierCompanyId, LocalDate workDate, Long equipmentId);

    /** 중복 방지 — 같은 공급사·같은 날·같은 인원. */
    boolean existsBySupplierCompanyIdAndWorkDateAndPersonId(Long supplierCompanyId, LocalDate workDate, Long personId);

    /** 월간 원장 — 장비 기준 기간 조회. */
    List<DailyWorkLog> findByEquipmentIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(Long equipmentId, LocalDate from, LocalDate to);

    /** 월간 원장 — 인원 기준 기간 조회. */
    List<DailyWorkLog> findByPersonIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(Long personId, LocalDate from, LocalDate to);

    /** P3d 이행 보고서 — 현장·기간 일일 확인서 배치 조회(서명율·미서명). */
    List<DailyWorkLog> findBySiteIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(Long siteId, LocalDate from, LocalDate to);
}
