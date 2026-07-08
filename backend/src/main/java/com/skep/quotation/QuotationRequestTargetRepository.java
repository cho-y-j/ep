package com.skep.quotation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface QuotationRequestTargetRepository extends JpaRepository<QuotationRequestTarget, Long> {
    List<QuotationRequestTarget> findByRequestIdOrderByIdAsc(Long requestId);
    List<QuotationRequestTarget> findByRequestIdInOrderByIdAsc(Collection<Long> requestIds);
    List<QuotationRequestTarget> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);
    long countByRequestIdAndStatus(Long requestId, QuotationTargetStatus status);
    long countByRequestIdAndStatusNot(Long requestId, QuotationTargetStatus status);
}
