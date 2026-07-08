package com.skep.quotation;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "quotation_request_targets",
       uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "supplier_company_id", "equipment_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuotationRequestTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    /** BP 가 특정 장비를 지목한 경우 (EQUIPMENT 견적 한정). 카테고리 broadcast 시 NULL 가능. */
    @Column(name = "equipment_id")
    private Long equipmentId;

    /** BP 가 특정 인원을 지목한 경우 (MANPOWER 견적 한정). 역할 broadcast 시 NULL 가능. */
    @Column(name = "person_id")
    private Long personId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QuotationTargetStatus status = QuotationTargetStatus.PENDING;

    @Column(name = "responded_by_user_id")
    private Long respondedByUserId;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "response_note", columnDefinition = "text")
    private String responseNote;

    @Column(name = "finalized_by_user_id")
    private Long finalizedByUserId;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "finalized_to_work_plan_id")
    private Long finalizedToWorkPlanId;

    @Column(name = "finalized_to_wpe_id")
    private Long finalizedToWpeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private QuotationRequestTarget(Long requestId, Long supplierCompanyId, Long equipmentId, Long personId) {
        this.requestId = requestId;
        this.supplierCompanyId = supplierCompanyId;
        this.equipmentId = equipmentId;
        this.personId = personId;
        this.status = QuotationTargetStatus.PENDING;
    }

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    public void markAccepted(Long userId, String note) {
        this.status = QuotationTargetStatus.ACCEPTED;
        this.respondedByUserId = userId;
        this.respondedAt = LocalDateTime.now();
        this.responseNote = note;
    }

    public void markRejected(Long userId, String note) {
        this.status = QuotationTargetStatus.REJECTED;
        this.respondedByUserId = userId;
        this.respondedAt = LocalDateTime.now();
        this.responseNote = note;
    }

    public void markFinalAccepted(Long userId, Long workPlanId, Long workPlanEquipmentId) {
        this.status = QuotationTargetStatus.FINAL_ACCEPTED;
        this.finalizedByUserId = userId;
        this.finalizedAt = LocalDateTime.now();
        this.finalizedToWorkPlanId = workPlanId;
        this.finalizedToWpeId = workPlanEquipmentId;
    }
}
