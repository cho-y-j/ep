package com.skep.document;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false, length = 16)
    private OwnerType appliesTo;

    @Column(name = "has_expiry", nullable = false)
    private boolean hasExpiry;

    @Column(name = "requires_verification", nullable = false)
    private boolean requiresVerification;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    // V14 정책 컬럼
    @Column(nullable = false)
    private boolean required;

    @Column(name = "blocks_assignment", nullable = false)
    private boolean blocksAssignment;

    @Column(name = "default_valid_months")
    private Integer defaultValidMonths;

    @Column(name = "ocr_enabled", nullable = false)
    private boolean ocrEnabled;

    /** verify-api OCR extract type (LICENSE | BUSINESS | CARGO | KOSHA | EQUIPMENT_REGISTRATION). null 이면 OCR 미사용. */
    @Column(name = "ocr_extract_type", length = 64)
    private String ocrExtractType;

    /** OCR 결과에서 만료일로 사용할 필드 키 (예: expiry_date, completion_date). */
    @Column(name = "ocr_expiry_field_key", length = 100)
    private String ocrExpiryFieldKey;

    /** main-api 정부 API endpoint 키 (RIMS_LICENSE | CARGO_LICENSE | KOSHA | NTS_BIZ). null 이면 자동 검증 안 함. */
    @Column(name = "verify_endpoint", length = 64)
    private String verifyEndpoint;

    /**
     * 검증/등록 시 채워야 하는 필드 목록을 JSON 배열 문자열로 저장.
     * 예: '["license_no","name","license_condition_code"]'.
     * OCR 추출 가능한 필드라도 실패 시 사용자가 보충 입력해야 한다.
     */
    @Column(name = "required_fields", columnDefinition = "text")
    private String requiredFields;

    /** S-11: EQUIPMENT 일 때 적용할 EquipmentCategory CSV. NULL = 모든 카테고리. 예: "CRANE,AERIAL_LIFT". */
    @Column(name = "applies_to_categories", columnDefinition = "text")
    private String appliesToCategories;

    /** S-11: PERSON 일 때 적용할 PersonRole CSV. NULL = 모든 역할. 예: "OPERATOR". */
    @Column(name = "applies_to_person_roles", columnDefinition = "text")
    private String appliesToPersonRoles;

    /** 영역-크롭 OCR 템플릿(영역맵) JSON. NULL 이면 영역 OCR 미사용(기존 full-OCR 경로). */
    @Column(name = "ocr_region_template", columnDefinition = "text")
    private String ocrRegionTemplate;

    /** V116: 서류 수집 '샘플 보기' — ADMIN 이 업로드한 마스킹된 예시 이미지의 스토리지 키. NULL = 미등록. */
    @Column(name = "sample_image_key", length = 255)
    private String sampleImageKey;

    /** V119: 서류 수집 '샘플 보기' 설명글 — ADMIN 이 작성한 촬영/제출 안내. 이미지와 독립(사진만/글만/둘다). NULL = 미등록. */
    @Column(name = "sample_description", columnDefinition = "text")
    private String sampleDescription;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private DocumentType(String name, OwnerType appliesTo, boolean hasExpiry,
                         boolean requiresVerification, Integer sortOrder, Boolean active,
                         boolean required, boolean blocksAssignment, Integer defaultValidMonths,
                         boolean ocrEnabled, String ocrExtractType, String ocrExpiryFieldKey,
                         String verifyEndpoint, String requiredFields) {
        this.name = name;
        this.appliesTo = appliesTo;
        this.hasExpiry = hasExpiry;
        this.requiresVerification = requiresVerification;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.active = active == null || active;
        this.required = required;
        this.blocksAssignment = blocksAssignment;
        this.defaultValidMonths = defaultValidMonths;
        this.ocrEnabled = ocrEnabled;
        this.ocrExtractType = ocrExtractType;
        this.ocrExpiryFieldKey = ocrExpiryFieldKey;
        this.verifyEndpoint = verifyEndpoint;
        this.requiredFields = requiredFields;
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

    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }

    public void setName(String n) { this.name = n; }
    public void setRequired(boolean v) { this.required = v; }
    public void setBlocksAssignment(boolean v) { this.blocksAssignment = v; }
    public void setHasExpiry(boolean v) { this.hasExpiry = v; }
    public void setRequiresVerification(boolean v) { this.requiresVerification = v; }
    public void setSortOrder(int v) { this.sortOrder = v; }
    public void setDefaultValidMonths(Integer v) { this.defaultValidMonths = v; }
    public void setAppliesToCategories(String v) { this.appliesToCategories = v; }
    public void setAppliesToPersonRoles(String v) { this.appliesToPersonRoles = v; }
    public void setOcrRegionTemplate(String v) { this.ocrRegionTemplate = v; }
    public void setSampleImageKey(String v) { this.sampleImageKey = v; }
    public void setSampleDescription(String v) { this.sampleDescription = v; }
}
