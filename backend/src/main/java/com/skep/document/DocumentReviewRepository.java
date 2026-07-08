package com.skep.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentReviewRepository extends JpaRepository<DocumentReview, Long> {

    /** BP사 수신함 — 자기 회사가 받은 서류 심사. */
    List<DocumentReview> findByBpCompanyIdOrderByIdDesc(Long bpCompanyId);

    /** ADMIN 전체 조회. */
    List<DocumentReview> findAllByOrderByIdDesc();
}
