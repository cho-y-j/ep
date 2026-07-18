package com.skep.resourcechange;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceChangeRequestRepository extends JpaRepository<ResourceChangeRequest, Long> {

    List<ResourceChangeRequest> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);

    List<ResourceChangeRequest> findByBpCompanyIdOrderByIdDesc(Long bpCompanyId);

    List<ResourceChangeRequest> findAllByOrderByIdDesc();
}
