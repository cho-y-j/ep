package com.skep.quotation.dispatch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DispatchedPersonRepository extends JpaRepository<DispatchedPerson, Long> {

    List<DispatchedPerson> findByQuotationRequestId(Long quotationRequestId);

    List<DispatchedPerson> findByPersonId(Long personId);

    List<DispatchedPerson> findByQuotationRequestIdAndSupplierCompanyId(Long quotationRequestId, Long supplierCompanyId);

    boolean existsByQuotationRequestIdAndSupplierCompanyId(Long quotationRequestId, Long supplierCompanyId);

    List<DispatchedPerson> findByQuotationRequestIdInOrderByIdDesc(java.util.Collection<Long> qrIds);

    List<DispatchedPerson> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);
}
