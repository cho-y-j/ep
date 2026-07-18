package com.skep.workplan;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

@Entity
@Table(name = "work_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "bp_company_id", nullable = false)
    private Long bpCompanyId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "work_location", length = 255)
    private String workLocation;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkPlanStatus status = WorkPlanStatus.DRAFT;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    // S-8.4: OnlyOffice 인플레이스 편집 세션의 현재 DOCX 파일 키.
    @Column(name = "current_docx_key", length = 255)
    private String currentDocxKey;

    @Column(name = "current_docx_template_id")
    private Long currentDocxTemplateId;

    /** S-9-B: skep 워크시트 132 필드 + role_assign + 첨부 선택 ID. PostgreSQL JSONB. */
    @Type(JsonBinaryType.class)
    @Column(name = "form_values", columnDefinition = "jsonb")
    private Map<String, Object> formValues;

    /** P1a 기반②: 서명 시점 워크시트 전문 PDF 스냅샷의 FileStorage key. null 이면 셸 렌더 폴백. */
    @Column(name = "sign_snapshot_key", length = 255)
    private String signSnapshotKey;

    @Column(name = "equipment_supplier_company_id")
    private Long equipmentSupplierCompanyId;

    @Column(name = "manpower_supplier_company_id")
    private Long manpowerSupplierCompanyId;

    @Column(name = "current_equipment_id")
    private Long currentEquipmentId;

    /** P1c: L2 자원 교체로 이 계획서가 원본을 대체해 생성됐을 때의 원본 id (이력 연결). */
    @Column(name = "cloned_from_id")
    private Long clonedFromId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private WorkPlan(Long siteId, Long bpCompanyId, LocalDate workDate,
                     LocalTime startTime, LocalTime endTime,
                     String title, String workLocation, String description,
                     Long createdBy) {
        this.siteId = siteId;
        this.bpCompanyId = bpCompanyId;
        this.workDate = workDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.title = title;
        this.workLocation = workLocation;
        this.description = description;
        this.createdBy = createdBy;
        this.status = WorkPlanStatus.DRAFT;
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

    public void update(LocalDate workDate, LocalTime startTime, LocalTime endTime,
                       String title, String workLocation, String description) {
        if (workDate != null) this.workDate = workDate;
        if (startTime != null) this.startTime = startTime;
        if (endTime != null) this.endTime = endTime;
        if (title != null) this.title = title;
        if (workLocation != null) this.workLocation = workLocation;
        if (description != null) this.description = description;
    }

    public void submit(Long userId) {
        this.status = WorkPlanStatus.SUBMITTED;
        this.submittedAt = LocalDateTime.now();
        this.submittedBy = userId;
    }

    public void approve(Long userId) {
        this.status = WorkPlanStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
        this.approvedBy = userId;
    }

    public void start() {
        this.status = WorkPlanStatus.IN_PROGRESS;
    }

    public void complete() {
        this.status = WorkPlanStatus.DONE;
    }

    public void cancel(Long userId, String reason) {
        this.status = WorkPlanStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelledBy = userId;
        this.cancelReason = reason;
    }

    /** 자원 추가/제거 가능한 상태인지 (DRAFT 만 자원 편집 가능). */
    public boolean isEditable() {
        return this.status == WorkPlanStatus.DRAFT;
    }

    /** OnlyOffice 편집 세션 등록 (또는 갱신). */
    public void setDocxState(String fileKey, Long templateId) {
        this.currentDocxKey = fileKey;
        this.currentDocxTemplateId = templateId;
    }

    /** S-9-B: 워크시트 폼 상태 저장/갱신. */
    public void setFormValues(Map<String, Object> values) {
        this.formValues = values;
    }

    /** P1a 기반②: 서명 스냅샷 key 저장/clear. */
    public void setSignSnapshotKey(String key) {
        this.signSnapshotKey = key;
    }

    public void setSupplierContext(Long equipmentSupplierCompanyId,
                                    Long manpowerSupplierCompanyId,
                                    Long currentEquipmentId) {
        this.equipmentSupplierCompanyId = equipmentSupplierCompanyId;
        this.manpowerSupplierCompanyId = manpowerSupplierCompanyId;
        this.currentEquipmentId = currentEquipmentId;
    }

    /** P1c: 교체 이력 연결 — 이 계획서가 대체한 원본 id 기록. */
    public void setClonedFrom(Long sourceId) {
        this.clonedFromId = sourceId;
    }
}
