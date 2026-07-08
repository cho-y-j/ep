package com.skep.quotation;

import com.skep.equipment.EquipmentCategory;
import com.skep.person.PersonRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "quotation_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuotationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** V33: site 는 견적 시점에 없어도 됨 (공개입찰은 site 결정 전 단계). 최종선정 시 자동 생성. */
    @Column(name = "site_id")
    private Long siteId;

    /** V33: 견적 발송 방식. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QuotationMode mode = QuotationMode.TARGETED;

    /** V33: 원청기관(삼성/SK). 양식 단계에선 옵션. 자원 이력 라벨용으로만 사용. */
    @Column(name = "client_org_id")
    private Long clientOrgId;

    /** V33: 현장 이름/주소 자유 텍스트. site 없을 때 BP 가 채워넣는 식별 정보. */
    @Column(name = "work_location_text", columnDefinition = "text")
    private String workLocationText;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    /** ADMIN 대행 시 어느 BP 컨텍스트로 발송했는지. BP 본인 작성 시 NULL. */
    @Column(name = "on_behalf_of_bp_company_id")
    private Long onBehalfOfBpCompanyId;

    /**
     * V35: 발신 BP 회사 직접 컬럼. site 또는 onBehalfOf 유추 불필요 — 모든 흐름이 같은 쿼리로 통일.
     * 신규 row 는 service 에서 항상 채워 저장. 옛 row 는 V35 마이그레이션이 backfill.
     */
    @Column(name = "bp_company_id")
    private Long bpCompanyId;

    @Column(name = "work_period_start", nullable = false)
    private LocalDate workPeriodStart;

    @Column(name = "work_period_end", nullable = false)
    private LocalDate workPeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 16)
    private QuotationRequestType requestType = QuotationRequestType.EQUIPMENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "equipment_category", length = 32)
    private EquipmentCategory equipmentCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "manpower_role", length = 32)
    private PersonRole manpowerRole;

    /** 같은 발송 묶음의 견적은 같은 UUID. 한 사이트에 장비+인력 N역할 한 번에 보낼 때 그룹화. */
    @Column(name = "bundle_id")
    private UUID bundleId;

    @Column(name = "spec_text", columnDefinition = "text")
    private String specText;

    @Column(name = "proposed_daily_rate")
    private Integer proposedDailyRate;

    @Column(name = "proposed_monthly_rate")
    private Integer proposedMonthlyRate;

    @Column(nullable = false)
    private Integer count = 1;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QuotationStatus status = QuotationStatus.SENT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private QuotationRequest(Long siteId, Long requestedByUserId, Long onBehalfOfBpCompanyId,
                              LocalDate workPeriodStart, LocalDate workPeriodEnd,
                              QuotationRequestType requestType,
                              EquipmentCategory equipmentCategory, PersonRole manpowerRole,
                              String specText,
                              Integer proposedDailyRate, Integer proposedMonthlyRate,
                              Integer count, String notes,
                              UUID bundleId,
                              QuotationMode mode, Long clientOrgId, String workLocationText,
                              Long bpCompanyId) {
        this.siteId = siteId;
        this.requestedByUserId = requestedByUserId;
        this.onBehalfOfBpCompanyId = onBehalfOfBpCompanyId;
        this.bpCompanyId = bpCompanyId;
        this.workPeriodStart = workPeriodStart;
        this.workPeriodEnd = workPeriodEnd;
        this.requestType = requestType != null ? requestType : QuotationRequestType.EQUIPMENT;
        this.equipmentCategory = equipmentCategory;
        this.manpowerRole = manpowerRole;
        this.specText = specText;
        this.proposedDailyRate = proposedDailyRate;
        this.proposedMonthlyRate = proposedMonthlyRate;
        this.count = count != null ? count : 1;
        this.notes = notes;
        this.status = QuotationStatus.SENT;
        this.bundleId = bundleId;
        this.mode = mode != null ? mode : QuotationMode.TARGETED;
        this.clientOrgId = clientOrgId;
        this.workLocationText = workLocationText;
    }

    public void updateLocation(Long clientOrgId, String workLocationText) {
        this.clientOrgId = clientOrgId;
        this.workLocationText = workLocationText;
    }

    public void linkToSite(Long siteId) { this.siteId = siteId; }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void markClosed() { this.status = QuotationStatus.CLOSED; }
    public void markCancelled() { this.status = QuotationStatus.CANCELLED; }
}
