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

    /** 정산: 본인+직속자식 명의(supplier) 행 + 자기 귀속(sub==본인) 행 전체(견적 무관). */
    @Query("select d from DispatchedPerson d where d.supplierCompanyId in :supplierIds "
            + "or d.subSupplierCompanyId = :selfId")
    List<DispatchedPerson> findAllVisibleForSupplier(@Param("supplierIds") java.util.Collection<Long> supplierIds,
                                                     @Param("selfId") Long selfId);

    List<DispatchedPerson> findByQuotationRequestIdInOrderByIdDesc(java.util.Collection<Long> qrIds);

    List<DispatchedPerson> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);

    /** 견적 후보 "이전 투입" 배지용: 이 BP(bp_company_id 또는 대행 onBehalfOf) 견적에 배차된 적 있는 인원 id 집합. */
    @Query("select distinct d.personId from DispatchedPerson d, QuotationRequest q "
            + "where d.quotationRequestId = q.id "
            + "and (q.bpCompanyId = :bp or q.onBehalfOfBpCompanyId = :bp) "
            + "and d.personId in :ids")
    java.util.Set<Long> findDispatchedPersonIdsForBp(@Param("bp") Long bp,
                                                     @Param("ids") java.util.Collection<Long> ids);
}
