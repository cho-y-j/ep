package com.skep.outgoing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutgoingQuotationRepository extends JpaRepository<OutgoingQuotation, Long> {
    List<OutgoingQuotation> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);
    List<OutgoingQuotation> findByRecipientUserIdOrderByIdDesc(Long recipientUserId);
    List<OutgoingQuotation> findByRecipientCompanyIdOrderByIdDesc(Long recipientCompanyId);
}
