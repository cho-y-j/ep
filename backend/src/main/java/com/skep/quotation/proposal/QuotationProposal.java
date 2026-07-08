package com.skep.quotation.proposal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "quotation_proposals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuotationProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    @Column(name = "proposed_by_user_id", nullable = false)
    private Long proposedByUserId;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "person_id")
    private Long personId;

    @Column(name = "daily_rate")
    private Integer dailyRate;

    @Column(name = "ot_daily_rate")
    private Integer otDailyRate;

    @Column(name = "monthly_rate")
    private Integer monthlyRate;

    @Column(name = "ot_monthly_rate")
    private Integer otMonthlyRate;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "daily_note", length = 255)
    private String dailyNote;

    @Column(name = "ot_daily_note", length = 255)
    private String otDailyNote;

    @Column(name = "monthly_note", length = 255)
    private String monthlyNote;

    @Column(name = "ot_monthly_note", length = 255)
    private String otMonthlyNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QuotationProposalStatus status = QuotationProposalStatus.SUBMITTED;

    @Column(name = "finalized_by_user_id")
    private Long finalizedByUserId;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "finalized_to_work_plan_id")
    private Long finalizedToWorkPlanId;

    @Column(name = "finalized_to_wpe_id")
    private Long finalizedToWpeId;

    @Column(name = "finalized_to_wpp_id")
    private Long finalizedToWppId;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private QuotationProposal(Long requestId, Long supplierCompanyId, Long proposedByUserId,
                               Long equipmentId, Long personId,
                               Integer dailyRate, Integer otDailyRate,
                               Integer monthlyRate, Integer otMonthlyRate,
                               String note,
                               String dailyNote, String otDailyNote,
                               String monthlyNote, String otMonthlyNote) {
        this.requestId = requestId;
        this.supplierCompanyId = supplierCompanyId;
        this.proposedByUserId = proposedByUserId;
        this.equipmentId = equipmentId;
        this.personId = personId;
        this.dailyRate = dailyRate;
        this.otDailyRate = otDailyRate;
        this.monthlyRate = monthlyRate;
        this.otMonthlyRate = otMonthlyRate;
        this.note = note;
        this.dailyNote = dailyNote;
        this.otDailyNote = otDailyNote;
        this.monthlyNote = monthlyNote;
        this.otMonthlyNote = otMonthlyNote;
        this.status = QuotationProposalStatus.SUBMITTED;
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

    public void updateOffer(Integer dailyRate, Integer otDailyRate,
                            Integer monthlyRate, Integer otMonthlyRate, String note,
                            String dailyNote, String otDailyNote,
                            String monthlyNote, String otMonthlyNote) {
        if (dailyRate != null) this.dailyRate = dailyRate;
        if (otDailyRate != null) this.otDailyRate = otDailyRate;
        if (monthlyRate != null) this.monthlyRate = monthlyRate;
        if (otMonthlyRate != null) this.otMonthlyRate = otMonthlyRate;
        if (note != null) this.note = note;
        if (dailyNote != null) this.dailyNote = dailyNote;
        if (otDailyNote != null) this.otDailyNote = otDailyNote;
        if (monthlyNote != null) this.monthlyNote = monthlyNote;
        if (otMonthlyNote != null) this.otMonthlyNote = otMonthlyNote;
        if (this.status == QuotationProposalStatus.PENDING_REVIEW) {
            this.status = QuotationProposalStatus.SUBMITTED;
        }
    }

    /** BP 가 견적 spec 수정 → 모든 제안 재확인 필요. */
    public void markPendingReview() {
        if (this.status == QuotationProposalStatus.SUBMITTED) {
            this.status = QuotationProposalStatus.PENDING_REVIEW;
        }
    }

    public void markFinalAccepted(Long userId, Long workPlanId, Long wpeId, Long wppId) {
        this.status = QuotationProposalStatus.FINAL_ACCEPTED;
        this.finalizedByUserId = userId;
        this.finalizedAt = LocalDateTime.now();
        this.finalizedToWorkPlanId = workPlanId;
        this.finalizedToWpeId = wpeId;
        this.finalizedToWppId = wppId;
    }

    public void markRejected() {
        this.status = QuotationProposalStatus.REJECTED;
        this.rejectedAt = LocalDateTime.now();
    }

    public void markWithdrawn() {
        this.status = QuotationProposalStatus.WITHDRAWN;
    }
}
