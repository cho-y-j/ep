package com.skep.quotation.proposal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuotationProposalRepository extends JpaRepository<QuotationProposal, Long> {

    List<QuotationProposal> findByRequestIdOrderByIdDesc(Long requestId);

    List<QuotationProposal> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);

    /** 같은 (request, supplier, equipment) 중복 차단용. */
    Optional<QuotationProposal> findByRequestIdAndSupplierCompanyIdAndEquipmentId(
            Long requestId, Long supplierCompanyId, Long equipmentId);

    /** 같은 (request, supplier, person) 중복 차단용. */
    Optional<QuotationProposal> findByRequestIdAndSupplierCompanyIdAndPersonId(
            Long requestId, Long supplierCompanyId, Long personId);

    long countByRequestIdAndStatus(Long requestId, QuotationProposalStatus status);

    /** 견적 목록 단계 집계용 — 여러 견적의 특정 상태 제안을 한 번에 배치 조회. */
    List<QuotationProposal> findByRequestIdInAndStatus(
            java.util.Collection<Long> requestIds, QuotationProposalStatus status);

    long countByRequestId(Long requestId);

    /** 같은 공급사가 같은 견적에 이미 활성(SUBMITTED/PENDING_REVIEW) 제안 보유 여부. */
    boolean existsByRequestIdAndSupplierCompanyIdAndStatusIn(
            Long requestId, Long supplierCompanyId, java.util.Collection<QuotationProposalStatus> statuses);

    /** finalize race-condition 방지 — 행 단위 비관적 락. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM QuotationProposal p WHERE p.id = :id")
    Optional<QuotationProposal> findByIdForUpdate(@Param("id") Long id);

    /** 견적 삭제 시 cascade 용 — OPEN_BID 견적 삭제 시 받은 제안 정리. */
    long countByRequestIdAndStatusIn(Long requestId, java.util.Collection<QuotationProposalStatus> statuses);

    void deleteByRequestId(Long requestId);
}
