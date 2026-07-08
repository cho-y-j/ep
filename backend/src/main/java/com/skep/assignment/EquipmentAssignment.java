package com.skep.assignment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 장비 ↔ 현장 배치 이력. released_at 이 null 이면 현재 활성 배치다.
 * UNIQUE INDEX(equipment_id) WHERE released_at IS NULL — 자원당 활성 배치는 하나뿐.
 */
@Entity
@Table(name = "equipment_site_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "site_id", nullable = false)
    private Long siteId;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Column(name = "released_by")
    private Long releasedBy;

    @Column(length = 255)
    private String note;

    @Column(name = "release_reason", length = 255)
    private String releaseReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** S-8.5: 작업계획서 시작 시 자동 생성된 배치 추적용. 수동 배치는 NULL. */
    @Column(name = "triggered_by_work_plan_id")
    private Long triggeredByWorkPlanId;

    @Builder
    private EquipmentAssignment(Long equipmentId, Long siteId, LocalDateTime assignedAt,
                                Long assignedBy, String note, Long triggeredByWorkPlanId) {
        this.equipmentId = equipmentId;
        this.siteId = siteId;
        this.assignedAt = assignedAt;
        this.assignedBy = assignedBy;
        this.note = note;
        this.triggeredByWorkPlanId = triggeredByWorkPlanId;
    }

    @PrePersist
    void onCreate() {
        if (this.assignedAt == null) this.assignedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    public void release(LocalDateTime when, Long releasedBy, String reason) {
        this.releasedAt = when;
        this.releasedBy = releasedBy;
        this.releaseReason = reason;
    }

    public boolean isActive() {
        return this.releasedAt == null;
    }
}
