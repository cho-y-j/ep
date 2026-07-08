package com.skep.safety;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 현장 진입 전 안전점검 / 검사 일정.
 *  - target_type: VEHICLE(장비) | PERSON(인원)
 *  - kind: VEHICLE_INSPECTION(차량검사 — 며칠 사전) | ENTRY_CHECK(입소검사 — 시간 단위)
 *  - status 전이: PENDING → SENT(BP가 공급사에 통보) → CONFIRMED(공급사 확인) → COMPLETED(검사 완료) | CANCELLED
 *  - 작업 시작 (WorkPlan start) 시점에 자원별 COMPLETED 여부 확인 — 미완료면 진입 차단.
 */
@Entity
@Table(name = "safety_inspections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_id", nullable = false)
    private Long siteId;

    @Column(name = "supplier_company_id")
    private Long supplierCompanyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private InspectionTarget targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InspectionKind kind;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "inspector_id")
    private Long inspectorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InspectionStatus status;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "result_notes", columnDefinition = "TEXT")
    private String resultNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private SafetyInspection(Long siteId, Long supplierCompanyId, InspectionTarget targetType, Long targetId,
                             InspectionKind kind, LocalDateTime scheduledAt, Integer durationMinutes,
                             Long inspectorId, Long createdBy) {
        this.siteId = siteId;
        this.supplierCompanyId = supplierCompanyId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.kind = kind;
        this.scheduledAt = scheduledAt;
        this.durationMinutes = durationMinutes;
        this.inspectorId = inspectorId;
        this.status = InspectionStatus.PENDING;
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

    /** BP 가 공급사에 통보 — 1회 멱등 (PENDING 일 때만 가능). */
    public void markSent() {
        if (this.status != InspectionStatus.PENDING) {
            throw new IllegalStateException("이미 통보된 일정입니다.");
        }
        this.status = InspectionStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    /** 공급사가 일정 확인. */
    public void markConfirmed() {
        if (this.status != InspectionStatus.SENT) {
            throw new IllegalStateException("통보된 일정만 확인 가능합니다.");
        }
        this.status = InspectionStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    /** 검사 완료. */
    public void markCompleted(String resultNotes) {
        this.status = InspectionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.resultNotes = resultNotes;
    }

    public void cancel() {
        this.status = InspectionStatus.CANCELLED;
    }
}
