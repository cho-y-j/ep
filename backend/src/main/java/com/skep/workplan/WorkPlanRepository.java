package com.skep.workplan;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkPlanRepository extends JpaRepository<WorkPlan, Long> {

    /** ADMIN 전체. 기본 정렬은 작업일 오름차순 (빠른 날짜 위). */
    Page<WorkPlan> findAllByOrderByWorkDateAscIdAsc(Pageable pageable);

    /** BP 자기 회사 작업계획서. 기본 정렬은 작업일 오름차순. */
    Page<WorkPlan> findByBpCompanyIdOrderByWorkDateAscIdAsc(Long bpCompanyId, Pageable pageable);

    /** BP 회사 작업계획서 전체 — 공지 발송 스코프 검증용. */
    List<WorkPlan> findByBpCompanyId(Long bpCompanyId);

    /** BP 회사의 작업계획서 중 특정 상태 — 투입 현황 board 용. 사인 완료된(SUBMITTED+) 것 가져오기. */
    List<WorkPlan> findByBpCompanyIdAndStatusInOrderByIdDesc(Long bpCompanyId, java.util.Collection<WorkPlanStatus> statuses);

    /** 현장 단위 작업계획서(상태 필터) — 원청 관제 허브가 현장 투입 자원 집계에 사용. */
    List<WorkPlan> findBySiteIdAndStatusInOrderByIdDesc(Long siteId, java.util.Collection<WorkPlanStatus> statuses);

    /**
     * 공급사 가시성: 자기 회사 자원이 포함된 작업계획서. WPE 또는 WPP 의 supplier_company_id 매칭.
     * 기본 정렬: 작업일 오름차순.
     */
    @Query("""
            SELECT DISTINCT wp FROM WorkPlan wp
            WHERE EXISTS (
                SELECT 1 FROM WorkPlanEquipment wpe
                WHERE wpe.workPlanId = wp.id AND wpe.supplierCompanyId = :companyId
            ) OR EXISTS (
                SELECT 1 FROM WorkPlanPerson wpp
                WHERE wpp.workPlanId = wp.id AND wpp.supplierCompanyId = :companyId
            )
            ORDER BY wp.workDate ASC, wp.id ASC
            """)
    Page<WorkPlan> findBySupplierCompanyId(@Param("companyId") Long companyId, Pageable pageable);

    /** BP dashboard 의 today/upcoming 용. */
    List<WorkPlan> findByBpCompanyIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
            Long bpCompanyId, LocalDate from, LocalDate to);

    /** 공급사 dashboard 의 upcoming 용. */
    @Query("""
            SELECT DISTINCT wp FROM WorkPlan wp
            WHERE wp.workDate BETWEEN :from AND :to
              AND (
                EXISTS (SELECT 1 FROM WorkPlanEquipment wpe
                        WHERE wpe.workPlanId = wp.id AND wpe.supplierCompanyId = :companyId)
                OR EXISTS (SELECT 1 FROM WorkPlanPerson wpp
                           WHERE wpp.workPlanId = wp.id AND wpp.supplierCompanyId = :companyId)
              )
            ORDER BY wp.workDate ASC, wp.startTime ASC
            """)
    List<WorkPlan> findUpcomingForSupplier(@Param("companyId") Long companyId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    /** ADMIN 전체 today/upcoming 용. */
    List<WorkPlan> findByWorkDateBetweenOrderByWorkDateAscStartTimeAsc(LocalDate from, LocalDate to);

    /** S-10: 견적 finalize 시 같은 사이트 + work_date + DRAFT 매칭 작업계획서 우선 활용. */
    Optional<WorkPlan> findFirstBySiteIdAndWorkDateAndStatusOrderByIdDesc(
            Long siteId, LocalDate workDate, WorkPlanStatus status);

    /** 현장 상황판 — 특정 현장의 특정일 작업계획서 전체(작업 진행/투입 자원 조립용). */
    List<WorkPlan> findBySiteIdAndWorkDateOrderByStartTimeAsc(Long siteId, LocalDate workDate);

    /** P3d 이행 보고서 — 현장·기간 계획서 배치 조회(서명 완결 집계). */
    List<WorkPlan> findBySiteIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(Long siteId, LocalDate from, LocalDate to);
}
