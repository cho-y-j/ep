package com.skep.quotation.bundle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentBundleRepository extends JpaRepository<DocumentBundle, Long> {
    Optional<DocumentBundle> findByQuotationRequestIdAndSupplierCompanyId(Long requestId, Long supplierCompanyId);

    List<DocumentBundle> findByQuotationRequestId(Long requestId);

    /** 견적 목록 단계 집계용 — 여러 견적의 서류 묶음을 한 번에 배치 조회. */
    List<DocumentBundle> findByQuotationRequestIdIn(java.util.Collection<Long> requestIds);

    boolean existsByQuotationRequestIdAndSupplierCompanyId(Long requestId, Long supplierCompanyId);
}
