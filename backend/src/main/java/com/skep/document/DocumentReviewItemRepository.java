package com.skep.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DocumentReviewItemRepository extends JpaRepository<DocumentReviewItem, Long> {

    List<DocumentReviewItem> findByReviewIdOrderByIdAsc(Long reviewId);

    /** 수신함 목록용 배치 로드. */
    List<DocumentReviewItem> findByReviewIdInOrderByIdAsc(Collection<Long> reviewIds);

    /**
     * V96: 이 자원(owner)이 해당 BP 회사 앞으로 온 심사 봉투에 포함되어 있는지.
     * BP 파일 열람 권한 분기(ensureCanAccess)에서 사용 — exists 단건 판정(전체 로드 금지).
     */
    @Query("""
            SELECT (COUNT(i) > 0) FROM DocumentReviewItem i
            WHERE i.ownerType = :ownerType AND i.ownerId = :ownerId
              AND i.reviewId IN (SELECT r.id FROM DocumentReview r WHERE r.bpCompanyId = :bpCompanyId)
            """)
    boolean existsForBpAndOwner(@Param("bpCompanyId") Long bpCompanyId,
                                @Param("ownerType") OwnerType ownerType,
                                @Param("ownerId") Long ownerId);
}
