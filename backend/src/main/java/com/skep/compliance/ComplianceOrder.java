package com.skep.compliance;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "compliance_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bp_company_id", nullable = false)
    private Long bpCompanyId;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private ComplianceTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 32)
    private ComplianceOrderType orderType;

    @Column(name = "order_subtype", length = 100)
    private String orderSubtype;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "request_notes", columnDefinition = "TEXT")
    private String requestNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ComplianceOrderStatus status = ComplianceOrderStatus.REQUESTED;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "submission_notes", columnDefinition = "TEXT")
    private String submissionNotes;

    @Column(name = "proof_storage_key", length = 500)
    private String proofStorageKey;

    @Column(name = "proof_filename", length = 255)
    private String proofFilename;

    @Column(name = "proof_content_type", length = 100)
    private String proofContentType;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ComplianceOrder(Long bpCompanyId, Long supplierCompanyId,
                            ComplianceTargetType targetType, Long targetId,
                            ComplianceOrderType orderType, String orderSubtype,
                            LocalDate dueDate, String requestNotes, Long createdBy) {
        this.bpCompanyId = bpCompanyId;
        this.supplierCompanyId = supplierCompanyId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.orderType = orderType;
        this.orderSubtype = orderSubtype;
        this.dueDate = dueDate;
        this.requestNotes = requestNotes;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void submit(String notes, String storageKey, String filename, String contentType) {
        this.status = ComplianceOrderStatus.SUBMITTED;
        this.submittedAt = LocalDateTime.now();
        this.submissionNotes = notes;
        if (storageKey != null) {
            this.proofStorageKey = storageKey;
            this.proofFilename = filename;
            this.proofContentType = contentType;
        }
        // 반려 → 재제출 시 reviewedAt/by 는 그대로 남기되 status 만 갱신.
    }

    public void approve(Long reviewerUserId) {
        this.status = ComplianceOrderStatus.APPROVED;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedBy = reviewerUserId;
        this.rejectionReason = null;
    }

    public void reject(Long reviewerUserId, String reason) {
        this.status = ComplianceOrderStatus.REJECTED;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedBy = reviewerUserId;
        this.rejectionReason = reason;
    }

    public boolean isOverdue(LocalDate today) {
        return (status == ComplianceOrderStatus.REQUESTED || status == ComplianceOrderStatus.REJECTED)
                && dueDate.isBefore(today);
    }
}
