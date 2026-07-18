package com.skep.fieldDeployment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FieldDeploymentRepository extends JpaRepository<FieldDeploymentRequest, Long> {
    List<FieldDeploymentRequest> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);
    List<FieldDeploymentRequest> findByBpCompanyIdOrderByIdDesc(Long bpCompanyId);

    List<FieldDeploymentRequest> findByBpCompanyIdAndStatusOrderByIdDesc(Long bpCompanyId, FieldDeploymentStatus status);

    // 자원 파이프라인 — 본인+자식 소유 자원의 ACTIVE 투입 일괄 조회(N+1 회피).
    List<FieldDeploymentRequest> findBySupplierCompanyIdInAndStatus(Collection<Long> supplierCompanyIds, FieldDeploymentStatus status);

    // 정산 디커플링(§3.2) — 수락된 투입(ACTIVE/COMPLETED) 을 정산 원천으로 일괄 조회.
    List<FieldDeploymentRequest> findBySupplierCompanyIdInAndStatusIn(Collection<Long> supplierCompanyIds, Collection<FieldDeploymentStatus> statuses);
}
