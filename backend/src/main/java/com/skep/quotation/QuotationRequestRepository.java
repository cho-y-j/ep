package com.skep.quotation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuotationRequestRepository extends JpaRepository<QuotationRequest, Long> {

    /** OPEN_BID finalize race-condition 방지 — 요청 행 잠금으로 동시 선정 직렬화 (count 초과 선정 차단). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM QuotationRequest q WHERE q.id = :id")
    Optional<QuotationRequest> findByIdForUpdate(@Param("id") Long id);
    List<QuotationRequest> findAllByOrderByIdDesc();
    List<QuotationRequest> findBySiteIdOrderByIdDesc(Long siteId);
    List<QuotationRequest> findBySiteIdInOrderByIdDesc(java.util.Collection<Long> siteIds);
    long countByStatus(QuotationStatus status);
    List<QuotationRequest> findByBundleIdOrderByIdAsc(java.util.UUID bundleId);

    /** V35: BP 회사 발신 견적 전체 (TARGETED + OPEN_BID 모두). bp_company_id 직접 컬럼. */
    List<QuotationRequest> findByBpCompanyIdOrderByIdDesc(Long bpCompanyId);

    /** BP 회사가 직접 발주했거나 ADMIN 대행으로 받은 견적 모두. 투입 자원 페이지 scope 용. */
    List<QuotationRequest> findByBpCompanyIdOrOnBehalfOfBpCompanyIdOrderByIdDesc(Long bp1, Long bp2);
}
