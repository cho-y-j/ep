package com.skep.quotation.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ComparisonSnapshotRepository extends JpaRepository<ComparisonSnapshot, Long> {
    Optional<ComparisonSnapshot> findByQuotationRequestId(Long quotationRequestId);

    /** 한 BP 회사가 만든 모든 비교 snapshot — 회사 거래 이력 탭에서 사용. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT s FROM ComparisonSnapshot s WHERE s.quotationRequestId IN " +
        "(SELECT qr.id FROM QuotationRequest qr WHERE qr.bpCompanyId = :bpCompanyId) " +
        "ORDER BY s.selectedAt DESC"
    )
    List<ComparisonSnapshot> findAllByBpCompanyId(@org.springframework.data.repository.query.Param("bpCompanyId") Long bpCompanyId);
}
