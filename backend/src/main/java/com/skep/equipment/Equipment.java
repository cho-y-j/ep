package com.skep.equipment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "equipment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "vehicle_no", length = 32)
    private String vehicleNo;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(length = 100)
    private String model;

    @Column(length = 100)
    private String manufacturer;

    private Integer year;

    @Column(name = "photo_key", length = 255)
    private String photoKey;

    @Column(name = "photo_content_type", length = 100)
    private String photoContentType;

    @Column(length = 64)
    private String code;

    /** NFC(RFC) 차량 태그 — 카드 도착 후 등록. 일상점검/출근 시 태그로 식별. */
    @Column(name = "nfc_tag_id", length = 64)
    private String nfcTagId;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "usage_hours")
    private Integer usageHours;

    @Column(name = "weight_kg")
    private Integer weightKg;

    @Column(name = "bucket_capacity", precision = 8, scale = 2)
    private java.math.BigDecimal bucketCapacity;

    @Column(name = "insurance_expiry")
    private java.time.LocalDate insuranceExpiry;

    // V70: 차량관리 만료/교체 due (정기검사/오일/등록)
    @Column(name = "inspection_due_date")
    private java.time.LocalDate inspectionDueDate;
    @Column(name = "oil_change_due_date")
    private java.time.LocalDate oilChangeDueDate;
    @Column(name = "registration_expiry")
    private java.time.LocalDate registrationExpiry;

    // V27: 자동차등록증상 차주(소유자) — 사업자(supplier_id) 와 다를 수 있음
    @Column(name = "vehicle_owner_name", length = 100)
    private String vehicleOwnerName;
    @Column(name = "vehicle_owner_business_no", length = 32)
    private String vehicleOwnerBusinessNo;
    @Column(name = "vehicle_owner_resident_no", length = 32)
    private String vehicleOwnerResidentNo;

    // 외부 조달 여부 — 우리 공급사 장비(false) / 외부에서 가져온 장비(true). 외부면 소유자(사업자) 서류 별도.
    @Column(name = "is_external", nullable = false)
    private boolean external;

    // Phase4: 외부 장비 기사(조종원) Person 연결 — 기사 로그인 계정.
    @Column(name = "operator_person_id")
    private Long operatorPersonId;

    @Column(name = "operating_hours", nullable = false)
    private int operatingHours;

    @Column(name = "idle_hours", nullable = false)
    private int idleHours;

    @Column(name = "downtime_hours", nullable = false)
    private int downtimeHours;

    // V11: 현재 배치 정보
    @Column(name = "current_site_id")
    private Long currentSiteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_status", nullable = false, length = 32)
    private EquipmentAssignmentStatus assignmentStatus = EquipmentAssignmentStatus.AVAILABLE;

    @Column(name = "last_assigned_at")
    private LocalDateTime lastAssignedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Equipment(Long supplierId, String vehicleNo, String category,
                      String model, String manufacturer, Integer year) {
        this.supplierId = supplierId;
        this.vehicleNo = vehicleNo;
        this.category = category;
        this.model = model;
        this.manufacturer = manufacturer;
        this.year = year;
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

    public void update(String vehicleNo, String category, String model, String manufacturer, Integer year) {
        if (vehicleNo != null) this.vehicleNo = vehicleNo;
        if (category != null) this.category = category;
        if (model != null) this.model = model;
        if (manufacturer != null) this.manufacturer = manufacturer;
        if (year != null) this.year = year;
    }

    /** 외부 조달 여부 + 소유자(차주/사업자) 정보 갱신. */
    public void updateSourcing(Boolean external, String ownerName, String ownerBusinessNo) {
        if (external != null) this.external = external;
        if (ownerName != null) this.vehicleOwnerName = ownerName.isBlank() ? null : ownerName.trim();
        if (ownerBusinessNo != null) this.vehicleOwnerBusinessNo = ownerBusinessNo.isBlank() ? null : ownerBusinessNo.trim();
    }

    /** Phase4: 외부 장비 기사(조종원) Person 연결/해제. */
    public void linkOperator(Long personId) {
        this.operatorPersonId = personId;
    }

    public void setPhoto(String key, String contentType) {
        this.photoKey = key;
        this.photoContentType = contentType;
    }

    public void clearPhoto() {
        this.photoKey = null;
        this.photoContentType = null;
    }

    /** 현장에 배치한다. assignment_status를 ASSIGNED로, current_site_id/last_assigned_at 업데이트. */
    public void assignToSite(Long siteId, LocalDateTime when) {
        this.currentSiteId = siteId;
        this.assignmentStatus = EquipmentAssignmentStatus.ASSIGNED;
        this.lastAssignedAt = when;
    }

    /** 현장 해제. assignment_status를 AVAILABLE로 되돌리되, BROKEN 상태였으면 유지. */
    public void releaseFromSite() {
        this.currentSiteId = null;
        if (this.assignmentStatus != EquipmentAssignmentStatus.BROKEN) {
            this.assignmentStatus = EquipmentAssignmentStatus.AVAILABLE;
        }
    }

    public void setAssignmentStatus(EquipmentAssignmentStatus status) {
        this.assignmentStatus = status;
    }

    public void assignNfcTag(String tagId) {
        this.nfcTagId = (tagId == null || tagId.isBlank()) ? null : tagId.trim();
    }

    public void setDueDates(java.time.LocalDate inspectionDue, java.time.LocalDate oilDue,
                            java.time.LocalDate regExpiry) {
        this.inspectionDueDate = inspectionDue;
        this.oilChangeDueDate = oilDue;
        this.registrationExpiry = regExpiry;
    }

    /** 검사만료일(정기검사 유효기간) 단건 설정 — 장비 등록 시 폼/OCR 값 저장용(다른 due 는 보존). */
    public void setInspectionDueDate(java.time.LocalDate d) {
        this.inspectionDueDate = d;
    }
}
