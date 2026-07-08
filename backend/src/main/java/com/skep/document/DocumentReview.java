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

    public DocumentReview(Long supplierCompanyId, Long bpCompanyId, String message, Long sentBy) {
        this.supplierCompanyId = supplierCompanyId;
        this.bpCompanyId = bpCompanyId;
        this.message = message;
        this.sentBy = sentBy;
    }

    public void markRead() {
        if (readAt == null) readAt = LocalDateTime.now();
    }
}
