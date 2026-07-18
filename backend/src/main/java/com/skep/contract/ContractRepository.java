package com.skep.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    /** 공급사 자기 회사 계약. */
    List<Contract> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);

    /** BP 자기 앞 계약(조회 전용). */
    List<Contract> findByBpCompanyIdOrderByIdDesc(Long bpCompanyId);
}
