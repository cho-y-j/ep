package com.skep.supplement;

import com.skep.document.OwnerType;
import com.skep.user.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_supplement_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentSupplementRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_user_id", nullable = false)
    private Long requesterUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requester_role", nullable = false, length = 32)
    private Role requesterRole;

    @Column(name = "target_supplier_company_id", nullable = false)
    private Long targetSupplierCompanyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_owner_type", nullable = false, length = 16)
    private OwnerType targetOwnerType;

    @Column(name = "target_owner_id", nullable = false)
    private Long targetOwnerId;

    @Column(name = "document_type_id", nullable = false)
    private Long documentTypeId;

    @Column(name = "context_site_id")
    private Long contextSiteId;

    @Column(name = "context_work_plan_id")
    private Long contextWorkPlanId;

    @Column(columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentSupplementStatus status = DocumentSupplementStatus.OPEN;

    @Column(name = "resolved_doc_id")
    private Long resolvedDocId;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private DocumentSupplementRequest(Long requesterUserId, Role requesterRole,
                                       Long targetSupplierCompanyId,
                                       OwnerType targetOwnerType, Long targetOwnerId,
                                       Long documentTypeId,
                                       Long contextSiteId, Long contextWorkPlanId,
                                       String reason) {
        this.requesterUserId = requesterUserId;
        this.requesterRole = requesterRole;
        this.targetSupplierCompanyId = targetSupplierCompanyId;
        this.targetOwnerType = targetOwnerType;
        this.targetOwnerId = targetOwnerId;
        this.documentTypeId = documentTypeId;
        this.contextSiteId = contextSiteId;
        this.contextWorkPlanId = contextWorkPlanId;
        this.reason = reason;
        this.status = DocumentSupplementStatus.OPEN;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public void markResolved(Long docId) {
        this.status = DocumentSupplementStatus.RESOLVED;
        this.resolvedDocId = docId;
        this.resolvedAt = LocalDateTime.now();
    }

    public void markCancelled() {
        this.status = DocumentSupplementStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }
}
