package com.skep.quotation.dispatch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DispatchedPersonRepository extends JpaRepository<DispatchedPerson, Long> {

    List<DispatchedPerson> findByQuotationRequestId(Long quotationRequestId);

    List<DispatchedPerson> findByPersonId(Long personId);

    List<DispatchedPerson> findByQuotationRequestIdAndSupplierCompanyId(Long quotationRequestId, Long supplierCompanyId);

    boolean existsByQuotationRequestIdAndSupplierCompanyId(Long quotationRequestId, Long supplierCompanyId);

    /** V77: 부모(supplier ∈ selfAndChildren)가 본 행 + 자식이 자기 귀속(sub == 본인) 행. */
    @Query("select d from DispatchedPerson d where d.quotationRequestId = :qrId "
            + "and (d.supplierCompanyId in :supplierIds or d.subSupplierCompanyId = :selfId)")
    List<DispatchedPerson> findVisibleForSupplier(@Param("qrId") Long qrId,
                                                  @Param("supplierIds") java.util.Collection<Long> supplierIds,
                                                  @Param("selfId") Long selfId);

    /** V77: 이 공급사에 귀속(sub_supplier)된 배차 행이 이 견적에 존재하는가 (자식 열람 인가용). */
    boolean existsByQuotationRequestIdAndSubSupplierCompanyId(Long quotationRequestId, Long subSupplierCompanyId);

    List<DispatchedPerson> findByQuotationRequestIdInOrderByIdDesc(java.util.Collection<Long> qrIds);

    List<DispatchedPerson> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);
}
