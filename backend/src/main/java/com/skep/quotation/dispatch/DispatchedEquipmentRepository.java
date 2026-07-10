package com.skep.quotation.dispatch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DispatchedEquipmentRepository extends JpaRepository<DispatchedEquipment, Long> {

    List<DispatchedEquipment> findByQuotationRequestId(Long quotationRequestId);

    List<DispatchedEquipment> findByEquipmentId(Long equipmentId);

    List<DispatchedEquipment> findByQuotationRequestIdAndSupplierCompanyId(Long quotationRequestId, Long supplierCompanyId);

    /** V77: 부모(supplier ∈ selfAndChildren)가 본 행 + 자식이 자기 귀속(sub == 본인) 행. */
    @Query("select d from DispatchedEquipment d where d.quotationRequestId = :qrId "
            + "and (d.supplierCompanyId in :supplierIds or d.subSupplierCompanyId = :selfId)")
    List<DispatchedEquipment> findVisibleForSupplier(@Param("qrId") Long qrId,
                                                     @Param("supplierIds") java.util.Collection<Long> supplierIds,
                                                     @Param("selfId") Long selfId);

    /** V77: 이 공급사에 귀속(sub_supplier)된 배차 행이 이 견적에 존재하는가 (자식 열람 인가용). */
    boolean existsByQuotationRequestIdAndSubSupplierCompanyId(Long quotationRequestId, Long subSupplierCompanyId);

    Optional<DispatchedEquipment> findByQuotationRequestIdAndEquipmentId(Long quotationRequestId, Long equipmentId);

    boolean existsByQuotationRequestIdAndSupplierCompanyId(Long quotationRequestId, Long supplierCompanyId);

    List<DispatchedEquipment> findByQuotationRequestIdInOrderBySentAtDesc(java.util.Collection<Long> qrIds);

    List<DispatchedEquipment> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);

    /** 해당 공급사가 이 BP 회사 소유 견적에 차량을 배차(=서류 묶음 발송 가능 관계)한 적 있는지.
     *  ADMIN 대행 견적(bpCompanyId 없이 onBehalfOfBpCompanyId 만 있는 경우)도 포함. */
    @Query("select count(d) > 0 from DispatchedEquipment d, QuotationRequest q "
            + "where d.quotationRequestId = q.id and d.supplierCompanyId = :supplierCompanyId "
            + "and (q.bpCompanyId = :bpCompanyId or q.onBehalfOfBpCompanyId = :bpCompanyId)")
    boolean existsSupplierDispatchedToBp(@Param("supplierCompanyId") Long supplierCompanyId,
                                         @Param("bpCompanyId") Long bpCompanyId);
}
