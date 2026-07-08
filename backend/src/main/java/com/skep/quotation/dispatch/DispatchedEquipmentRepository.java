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
