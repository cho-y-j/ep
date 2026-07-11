package com.skep.quotation.dispatch.draft;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 선정 직후 자동 생성되는 배차 초안 (V80). 별도 테이블 — 기존 dispatched 리더는 이 행을 보지 못한다.
 * confirm 시 기존 send() 로 실제 dispatched 를 만들고 이 행은 CONFIRMED.
 * 수동 send 로 대체되면 DISCARDED.
 */
@Entity
@Table(name = "quotation_dispatch_drafts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DispatchDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quotation_request_id", nullable = false)
    private Long quotationRequestId;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 16)
    private DispatchDraftResourceType resourceType;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "person_id")
    private Long personId;

    @Column(name = "daily_price")
    private Long dailyPrice;

    @Column(name = "monthly_price")
    private Long monthlyPrice;

    @Column(name = "ot_daily_price")
    private Long otDailyPrice;

    @Column(name = "ot_monthly_price")
    private Long otMonthlyPrice;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "source_proposal_id")
    private Long sourceProposalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DispatchDraftStatus status = DispatchDraftStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private DispatchDraft(Long quotationRequestId, Long supplierCompanyId, DispatchDraftResourceType resourceType,
                          Long equipmentId, Long personId,
                          Long dailyPrice, Long monthlyPrice, Long otDailyPrice, Long otMonthlyPrice,
                          String notes, Long sourceProposalId) {
        this.quotationRequestId = quotationRequestId;
        this.supplierCompanyId = supplierCompanyId;
        this.resourceType = resourceType;
        this.equipmentId = equipmentId;
        this.personId = personId;
        this.dailyPrice = dailyPrice;
        this.monthlyPrice = monthlyPrice;
        this.otDailyPrice = otDailyPrice;
        this.otMonthlyPrice = otMonthlyPrice;
        this.notes = notes;
        this.sourceProposalId = sourceProposalId;
        this.status = DispatchDraftStatus.DRAFT;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void markConfirmed() {
        this.status = DispatchDraftStatus.CONFIRMED;
    }

    public void markDiscarded() {
        this.status = DispatchDraftStatus.DISCARDED;
    }
}
