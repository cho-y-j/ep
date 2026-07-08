package com.skep.clientorg.history;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "equipment_client_org_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentClientOrgHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "client_org_id", nullable = false)
    private Long clientOrgId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HistorySource source;

    /** WORK_PLAN 인 경우 work_plan_id 등 source 의 참조 id. ADMIN 은 null. */
    @Column(name = "source_ref_id")
    private Long sourceRefId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private EquipmentClientOrgHistory(Long equipmentId, Long clientOrgId,
                                       LocalDate periodStart, LocalDate periodEnd,
                                       HistorySource source, Long sourceRefId, Long createdBy) {
        this.equipmentId = equipmentId;
        this.clientOrgId = clientOrgId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.source = source;
        this.sourceRefId = sourceRefId;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    public void updatePeriod(LocalDate start, LocalDate end) {
        if (start != null) this.periodStart = start;
        this.periodEnd = end;
    }
}
