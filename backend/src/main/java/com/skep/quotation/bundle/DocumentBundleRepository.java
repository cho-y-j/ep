package com.skep.quotation.bundle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentBundleRepository extends JpaRepository<DocumentBundle, Long> {
    Optional<DocumentBundle> findByQuotationRequestIdAndSupplierCompanyId(Long requestId, Long supplierCompanyId);

    List<DocumentBundle> findByQuotationRequestId(Long requestId);

    boolean existsByQuotationRequestIdAndSupplierCompanyId(Long requestId, Long supplierCompanyId);
}
