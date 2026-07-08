package com.skep.workconfirmation;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_confirmations")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class WorkConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_plan_id", nullable = false)
    private Long workPlanId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "issuing_supplier_company_id", nullable = false)
    private Long issuingSupplierCompanyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "issuing_supplier_type", nullable = false, length = 20)
    private IssuingSupplierType issuingSupplierType;

    @Column(name = "bp_company_id", nullable = false)
    private Long bpCompanyId;

    @Column(name = "work_content", columnDefinition = "TEXT")
    private String workContent;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "morning_time", length = 64)
    private String morningTime;
    @Column(name = "morning_hours", precision = 4, scale = 2)
    private BigDecimal morningHours;
    @Column(name = "afternoon_time", length = 64)
    private String afternoonTime;
    @Column(name = "afternoon_hours", precision = 4, scale = 2)
    private BigDecimal afternoonHours;
    @Column(name = "overtime_time", length = 64)
    private String overtimeTime;
    @Column(name = "overtime_hours", precision = 4, scale = 2)
    private BigDecimal overtimeHours;
    @Column(name = "night_time", length = 64)
    private String nightTime;
    @Column(name = "night_hours", precision = 4, scale = 2)
    private BigDecimal nightHours;
    @Column(name = "total_hours", precision = 5, scale = 2)
    private BigDecimal totalHours;

    @Column(name = "supplier_signer_name", length = 100)
    private String supplierSignerName;
    @Column(name = "supplier_signer_person_id")
    private Long supplierSignerPersonId;
    @Column(name = "supplier_signer_user_id")
    private Long supplierSignerUserId;
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "supplier_signature_png", columnDefinition = "bytea")
    private byte[] supplierSignaturePng;
    @Column(name = "supplier_signed_at")
    private LocalDateTime supplierSignedAt;

    @Column(name = "bp_signer_name", length = 100)
    private String bpSignerName;
    @Column(name = "bp_signer_user_id")
    private Long bpSignerUserId;
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "bp_signature_png", columnDefinition = "bytea")
    private byte[] bpSignaturePng;
    @Column(name = "bp_signed_at")
    private LocalDateTime bpSignedAt;

    @Column(name = "attendance_photo_doc_id")
    private Long attendancePhotoDocId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkConfirmationStatus status = WorkConfirmationStatus.PENDING;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
