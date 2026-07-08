package com.skep.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DocumentReviewItemRepository extends JpaRepository<DocumentReviewItem, Long> {

    List<DocumentReviewItem> findByReviewIdOrderByIdAsc(Long reviewId);

    /** 수신함 목록용 배치 로드. */
    List<DocumentReviewItem> findByReviewIdInOrderByIdAsc(Collection<Long> reviewIds);
}
