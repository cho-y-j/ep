package com.skep.document;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 서류 심사 봉투에 담긴 자원 1건 (장비 or 인원). 다운로드 시 owner 기준으로 서류를 재수집한다. */
@Entity
@Table(name = "document_review_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentReviewItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(name = "doc_count", nullable = false)
    private int docCount;

    public DocumentReviewItem(Long reviewId, OwnerType ownerType, Long ownerId, String label, int docCount) {
        this.reviewId = reviewId;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.label = label;
        this.docCount = docCount;
    }
}
