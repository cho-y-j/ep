package com.skep.workplan;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "work_plan_persons",
       uniqueConstraints = @UniqueConstraint(columnNames = {"work_plan_id", "person_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkPlanPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_plan_id", nullable = false)
    private Long workPlanId;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    /** 이 인원이 매칭된 장비 (조종원/신호수 등). 현장 안전관리자처럼 장비 매칭 없는 경우 null. */
    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(length = 32)
    private String role;

    @Column(length = 255)
    private String note;

    /** V33: 공개입찰 제안 finalize 로 만들어졌는지 추적. */
    @Column(name = "source_proposal_id")
    private Long sourceProposalId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private WorkPlanPerson(Long workPlanId, Long personId, Long supplierCompanyId,
                           Long equipmentId, String role, String note, Long sourceProposalId) {
        this.workPlanId = workPlanId;
        this.personId = personId;
        this.supplierCompanyId = supplierCompanyId;
        this.equipmentId = equipmentId;
        this.role = role;
        this.note = note;
        this.sourceProposalId = sourceProposalId;
    }

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }
}
