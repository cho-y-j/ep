package com.skep.quotation.bundle;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 공급사가 dispatched 차량의 서류를 BP 에 명시적으로 보낸 기록.
 * 견적 1건당 공급사 1회 멱등 (UNIQUE).
 */
@Entity
@Table(name = "quotation_document_bundles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quotation_request_id", nullable = false)
    private Long quotationRequestId;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "sent_by")
    private Long sentBy;

    @Column(name = "include_email", nullable = false)
    private boolean includeEmail;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private DocumentBundle(Long quotationRequestId, Long supplierCompanyId, Long sentBy,
                           boolean includeEmail, String notes) {
        this.quotationRequestId = quotationRequestId;
        this.supplierCompanyId = supplierCompanyId;
        this.sentBy = sentBy;
        this.includeEmail = includeEmail;
        this.notes = notes;
    }

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.sentAt = now;
        this.createdAt = now;
    }

    public void markEmailSent() {
        this.emailSentAt = LocalDateTime.now();
    }
}
