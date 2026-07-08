package com.skep.workplan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkPlanEquipmentRepository extends JpaRepository<WorkPlanEquipment, Long> {
    List<WorkPlanEquipment> findByWorkPlanIdOrderByIdAsc(Long workPlanId);
    Optional<WorkPlanEquipment> findByWorkPlanIdAndEquipmentId(Long workPlanId, Long equipmentId);
    void deleteByWorkPlanIdAndEquipmentId(Long workPlanId, Long equipmentId);

    /** 대시보드용 — work_plan_id 별 행 수. */
    List<WorkPlanEquipment> findByWorkPlanIdIn(java.util.Collection<Long> workPlanIds);

    /** P1: 공급사가 해당 작업계획서에 참여 중인지 검증용. */
    boolean existsByWorkPlanIdAndSupplierCompanyId(Long workPlanId, Long supplierCompanyId);

    /**
     * Phase3 장비 투입 통계 — 공급사 장비별·BP회사별 작업계획서 투입 건수 (DRAFT·CANCELLED 제외).
     * 반환 컬럼: [equipmentId, vehicleNo, model, category, isExternal, ownerName, bpCompanyId, bpCompanyName, deployCount]
     */
    @Query(value = """
            SELECT e.id, e.vehicle_no, e.model, e.category, e.is_external, e.vehicle_owner_name,
                   wp.bp_company_id, c.name, COUNT(*)
            FROM work_plan_equipment wpe
            JOIN work_plans wp ON wp.id = wpe.work_plan_id
            JOIN equipment e ON e.id = wpe.equipment_id
            LEFT JOIN companies c ON c.id = wp.bp_company_id
            WHERE wpe.supplier_company_id = :supplierId
              AND wp.status NOT IN ('DRAFT', 'CANCELLED')
            GROUP BY e.id, e.vehicle_no, e.model, e.category, e.is_external, e.vehicle_owner_name, wp.bp_company_id, c.name
            ORDER BY COUNT(*) DESC, e.id
            """, nativeQuery = true)
    List<Object[]> findDeploymentStats(@Param("supplierId") Long supplierId);
}
