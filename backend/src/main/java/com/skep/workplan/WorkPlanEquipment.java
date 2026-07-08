package com.skep.workplan;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "work_plan_equipment",
       uniqueConstraints = @UniqueConstraint(columnNames = {"work_plan_id", "equipment_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkPlanEquipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_plan_id", nullable = false)
    private Long workPlanId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    @Column(length = 100)
    private String purpose;

    @Column(length = 255)
    private String note;

    /** S-10: 견적 수락 시 결정된 일대 단가 (원). 직접 추가 시 NULL. */
    @Column(name = "daily_rate")
    private Integer dailyRate;

    /** S-10: 견적 수락 시 결정된 월대 단가 (원). 직접 추가 시 NULL. */
    @Column(name = "monthly_rate")
    private Integer monthlyRate;

    /** S-10: 어느 견적 target finalize 로 만들어졌는지 추적. 직접 추가 시 NULL. */
    @Column(name = "source_quotation_target_id")
    private Long sourceQuotationTargetId;

    /** V33: 공개입찰 제안 finalize 로 만들어졌는지 추적. */
    @Column(name = "source_proposal_id")
    private Long sourceProposalId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private WorkPlanEquipment(Long workPlanId, Long equipmentId, Long supplierCompanyId,
                              String purpose, String note,
                              Integer dailyRate, Integer monthlyRate,
                              Long sourceQuotationTargetId, Long sourceProposalId) {
        this.workPlanId = workPlanId;
        this.equipmentId = equipmentId;
        this.supplierCompanyId = supplierCompanyId;
        this.purpose = purpose;
        this.note = note;
        this.dailyRate = dailyRate;
        this.monthlyRate = monthlyRate;
        this.sourceQuotationTargetId = sourceQuotationTargetId;
        this.sourceProposalId = sourceProposalId;
    }

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }
}
