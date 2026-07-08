package com.skep.compliance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface ComplianceOrderRepository extends JpaRepository<ComplianceOrder, Long> {

    List<ComplianceOrder> findByBpCompanyIdOrderByIdDesc(Long bpCompanyId);

    List<ComplianceOrder> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);

    List<ComplianceOrder> findByTargetTypeAndTargetIdOrderByIdDesc(ComplianceTargetType targetType, Long targetId);

    @Query("SELECT c FROM ComplianceOrder c " +
           "WHERE c.targetType = :targetType AND c.targetId IN :targetIds " +
           "AND (c.status = com.skep.compliance.ComplianceOrderStatus.REQUESTED " +
           "  OR c.status = com.skep.compliance.ComplianceOrderStatus.REJECTED " +
           "  OR (c.status = com.skep.compliance.ComplianceOrderStatus.SUBMITTED AND c.dueDate < :today))")
    List<ComplianceOrder> findBlockingForTargets(ComplianceTargetType targetType, List<Long> targetIds, LocalDate today);
}
