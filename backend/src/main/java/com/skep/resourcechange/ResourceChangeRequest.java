package com.skep.resourcechange;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 업체변경 신청서 v0 (L2a, §3.6·§7). 현장×자원 교체 신청 데이터 + 신청 시점 L3(deploy-check) 스냅샷.
 * 실양식 수령 전 임의양식 v0 인쇄뷰의 데이터 원천. 서명 수집은 후속.
 */
@Entity
@Table(name = "resource_change_requests")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "site_name", length = 255)
    private String siteName;

    @Column(name = "bp_company_id")
    private Long bpCompanyId;

    @Column(name = "bp_name", length = 255)
    private String bpName;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_kind", nullable = false, length = 16)
    private ResourceChangeKind changeKind;

    @Column(name = "old_equipment_id")
    private Long oldEquipmentId;

    @Column(name = "new_equipment_id")
    private Long newEquipmentId;

    @Column(name = "old_person_id")
    private Long oldPersonId;

    @Column(name = "new_person_id")
    private Long newPersonId;

    @Column(name = "old_label", length = 255)
    private String oldLabel;

    @Column(name = "new_label", length = 255)
    private String newLabel;

    @Column(name = "old_vehicle_no", length = 64)
    private String oldVehicleNo;

    @Column(name = "new_vehicle_no", length = 64)
    private String newVehicleNo;

    @Column(name = "old_operator_name", length = 120)
    private String oldOperatorName;

    @Column(name = "new_operator_name", length = 120)
    private String newOperatorName;

    @Column(name = "old_contact", length = 64)
    private String oldContact;

    @Column(name = "new_contact", length = 64)
    private String newContact;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "apply_date")
    private LocalDate applyDate;

    /** 신청 시점 신규자원 deploy-check 결과({ready, blocks[], checkedAt}). */
    @Type(JsonBinaryType.class)
    @Column(name = "l3_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> l3Snapshot;

    @Column(name = "work_plan_id")
    private Long workPlanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResourceChangeStatus status = ResourceChangeStatus.DRAFT;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = ResourceChangeStatus.DRAFT;
    }
}
