package com.skep.resourceCheck;

import com.skep.document.OwnerType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "resource_check_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Setter
public class ResourceCheckRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_plan_id")
    private Long workPlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    /** 발행사 회사 id — BP 발행이면 BP사, 공급사 자체 발행이면 공급사 자신(재해석: "발행사"). 승인/반려 주체 판정 기준. */
    @Column(name = "bp_company_id", nullable = false)
    private Long bpCompanyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false, length = 30)
    private ResourceCheckType checkType;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** V125: 검사 시간(선택) — 날짜만 있는 기존 행은 NULL. */
    @Column(name = "due_time")
    private LocalTime dueTime;

    @Column(columnDefinition = "text")
    private String notes;

    /** V125: 발행사 통화·연락 기록 — "[7/24 14:00 이름] 내용" 줄 append. */
    @Column(name = "contact_log", columnDefinition = "text")
    private String contactLog;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceCheckStatus status = ResourceCheckStatus.REQUESTED;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "issued_by", nullable = false)
    private Long issuedBy;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    /** R2 조합 스냅샷 — 발행 시점 조합의 장비 id(장비 행=자기 id, 조종원 행=그 장비 id, 단독 발행=NULL).
     *  원장(equipment_default_operators)의 사후 변경이 과거 발행 기록을 오염시키지 않게 행에 고정. */
    @Column(name = "combo_equipment_id")
    private Long comboEquipmentId;

    public static ResourceCheckRequest issue(Long workPlanId, OwnerType ownerType, Long ownerId,
                                              Long supplierCompanyId, Long bpCompanyId,
                                              ResourceCheckType checkType, LocalDate dueDate,
                                              String notes, Long issuedBy) {
        var r = new ResourceCheckRequest();
        r.workPlanId = workPlanId;
        r.ownerType = ownerType;
        r.ownerId = ownerId;
        r.supplierCompanyId = supplierCompanyId;
        r.bpCompanyId = bpCompanyId;
        r.checkType = checkType;
        r.dueDate = dueDate;
        r.notes = notes;
        r.issuedBy = issuedBy;
        r.issuedAt = LocalDateTime.now();
        r.status = ResourceCheckStatus.REQUESTED;
        return r;
    }

    public void submit(Long documentId) {
        this.documentId = documentId;
        this.submittedAt = LocalDateTime.now();
        this.status = ResourceCheckStatus.SUBMITTED;
    }

    public void approve(Long reviewerId, String note) {
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.reviewNote = note;
        this.status = ResourceCheckStatus.APPROVED;
    }

    public void reject(Long reviewerId, String note) {
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.reviewNote = note;
        this.status = ResourceCheckStatus.REJECTED;
    }

    public void cancel() {
        this.status = ResourceCheckStatus.CANCELLED;
    }
}
