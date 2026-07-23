package com.skep.fieldDeployment;

import com.skep.document.OwnerType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 현장 투입 요청 — 공급사가 견적 수락된 자원을 "지금 현장으로 보낼게요" 요청.
 * BP 가 ACCEPTED 하면 ACTIVE 상태 (현장 투입). COMPLETED 로 종료.
 */
@Entity
@Table(name = "field_deployment_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldDeploymentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    @Column(name = "bp_company_id", nullable = false)
    private Long bpCompanyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private OwnerType resourceType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "target_site_id")
    private Long targetSiteId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(columnDefinition = "TEXT")
    private String note;

    // 투입 단가 (선택) — 일대/월대/OT/야간. 발송 시점 협의 단가 기록용.
    @Column(name = "daily_price")
    private Long dailyPrice;

    @Column(name = "monthly_price")
    private Long monthlyPrice;

    @Column(name = "ot_price")
    private Long otPrice;

    @Column(name = "night_price")
    private Long nightPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FieldDeploymentStatus status;

    /** R3 조합 스냅샷 — 장비 행=자기 equipment id, 조종원 행=그 장비 id, 단독 요청=NULL. 요청 시점 고정. */
    @Column(name = "combo_equipment_id")
    private Long comboEquipmentId;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    private FieldDeploymentRequest(Long supplierCompanyId, Long bpCompanyId,
                                    OwnerType resourceType, Long resourceId,
                                    Long targetSiteId, LocalDate startDate, String note,
                                    Long dailyPrice, Long monthlyPrice, Long otPrice, Long nightPrice,
                                    Long comboEquipmentId, Long requestedByUserId) {
        this.supplierCompanyId = supplierCompanyId;
        this.bpCompanyId = bpCompanyId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.targetSiteId = targetSiteId;
        this.startDate = startDate;
        this.note = note;
        this.dailyPrice = dailyPrice;
        this.monthlyPrice = monthlyPrice;
        this.otPrice = otPrice;
        this.nightPrice = nightPrice;
        this.comboEquipmentId = comboEquipmentId;
        this.status = FieldDeploymentStatus.REQUESTED;
        this.requestedByUserId = requestedByUserId;
        this.requestedAt = LocalDateTime.now();
    }

    public void accept(Long userId, String note, Long overrideTargetSiteId) {
        this.status = FieldDeploymentStatus.ACTIVE;
        this.reviewedByUserId = userId;
        this.reviewedAt = LocalDateTime.now();
        this.reviewNote = note;
        this.activatedAt = LocalDateTime.now();
        if (overrideTargetSiteId != null) this.targetSiteId = overrideTargetSiteId;
    }

    public void reject(Long userId, String note) {
        this.status = FieldDeploymentStatus.REJECTED;
        this.reviewedByUserId = userId;
        this.reviewedAt = LocalDateTime.now();
        this.reviewNote = note;
    }

    public void cancel() {
        this.status = FieldDeploymentStatus.CANCELLED;
    }

    public void complete() {
        this.status = FieldDeploymentStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }
}
