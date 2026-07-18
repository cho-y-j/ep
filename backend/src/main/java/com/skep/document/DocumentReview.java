package com.skep.document;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 공급사 → BP사 서류 심사 발송 1건(봉투). 이메일과 별개로 BP사 계정 수신함에서 조회/다운로드된다. */
@Entity
@Table(name = "document_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    @Column(name = "bp_company_id", nullable = false)
    private Long bpCompanyId;

    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "sent_by", nullable = false)
    private Long sentBy;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    @Column(name = "read_at")
    private LocalDateTime readAt;

    /** V96: 심사 상태머신 — PENDING(심사중) → APPROVED/REJECTED. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentReviewStatus status = DocumentReviewStatus.PENDING;

    @Column(name = "rejected_reason", length = 255)
    private String rejectedReason;

    @Column(name = "acted_by")
    private Long actedBy;

    @Column(name = "acted_at")
    private LocalDateTime actedAt;

    public DocumentReview(Long supplierCompanyId, Long bpCompanyId, String message, Long sentBy) {
        this.supplierCompanyId = supplierCompanyId;
        this.bpCompanyId = bpCompanyId;
        this.message = message;
        this.sentBy = sentBy;
    }

    public void markRead() {
        if (readAt == null) readAt = LocalDateTime.now();
    }

    public void approve(Long userId) {
        this.status = DocumentReviewStatus.APPROVED;
        this.actedBy = userId;
        this.actedAt = LocalDateTime.now();
        this.rejectedReason = null;
        markRead();
    }

    public void reject(Long userId, String reason) {
        this.status = DocumentReviewStatus.REJECTED;
        this.actedBy = userId;
        this.actedAt = LocalDateTime.now();
        this.rejectedReason = reason;
        markRead();
    }
}
