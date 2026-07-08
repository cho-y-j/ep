package com.skep.fieldDeployment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FieldDeploymentRepository extends JpaRepository<FieldDeploymentRequest, Long> {
    List<FieldDeploymentRequest> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);
    List<FieldDeploymentRequest> findByBpCompanyIdOrderByIdDesc(Long bpCompanyId);

    List<FieldDeploymentRequest> findByBpCompanyIdAndStatusOrderByIdDesc(Long bpCompanyId, FieldDeploymentStatus status);
}
