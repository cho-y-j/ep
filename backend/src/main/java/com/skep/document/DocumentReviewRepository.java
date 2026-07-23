package com.skep.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DocumentReviewRepository extends JpaRepository<DocumentReview, Long> {

    /** BP사 수신함 — 자기 회사가 받은 서류 심사. */
    List<DocumentReview> findByBpCompanyIdOrderByIdDesc(Long bpCompanyId);

    /** 공급사(본인+자식)가 보낸 서류 심사 — 자원 파이프라인의 심사 단계 표시용. */
    List<DocumentReview> findBySupplierCompanyIdInOrderByIdDesc(Collection<Long> supplierCompanyIds);

    /** ADMIN 전체 조회. */
    List<DocumentReview> findAllByOrderByIdDesc();
}
