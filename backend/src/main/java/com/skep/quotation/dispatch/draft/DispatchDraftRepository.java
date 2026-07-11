package com.skep.quotation.dispatch.draft;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DispatchDraftRepository extends JpaRepository<DispatchDraft, Long> {

    List<DispatchDraft> findByQuotationRequestId(Long quotationRequestId);

    List<DispatchDraft> findByQuotationRequestIdAndSupplierCompanyId(Long quotationRequestId, Long supplierCompanyId);

    /** finalize 중복 방지용 dedup — (요청,공급사) 초안 존재 여부. */
    boolean existsByQuotationRequestIdAndSupplierCompanyId(Long quotationRequestId, Long supplierCompanyId);

    /** GET 조회 — BP/ADMIN 전체 중 미확정만. */
    List<DispatchDraft> findByQuotationRequestIdAndStatus(Long quotationRequestId, DispatchDraftStatus status);

    /** GET 조회 — 공급사 본인/직속자식 명의 초안 중 미확정만. */
    List<DispatchDraft> findByQuotationRequestIdAndSupplierCompanyIdInAndStatus(
            Long quotationRequestId, java.util.Collection<Long> supplierCompanyIds, DispatchDraftStatus status);

    /** send 성공 후 잔존 초안 폐기 대상 — (요청,공급사) 의 DRAFT 초안. */
    List<DispatchDraft> findByQuotationRequestIdAndSupplierCompanyIdAndStatus(
            Long quotationRequestId, Long supplierCompanyId, DispatchDraftStatus status);
}
