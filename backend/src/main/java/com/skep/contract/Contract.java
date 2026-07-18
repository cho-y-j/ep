package com.skep.contract;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 계약 — 단가의 원천(§3.3.1). 공급사가 직접 등록(견적 없이도 가능), BP 는 자기 앞 계약 조회.
 * 기본단가(일대/월대) + OT 5분류 단가(조출·점심·연장·야간·철야). 계약서 스캔 파일 첨부.
 */
@Entity
@Table(name = "contracts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    @Column(name = "bp_company_id")
    private Long bpCompanyId;

    @Column(name = "bp_name", length = 255)
    private String bpName;

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "site_name", length = 255)
    private String siteName;

    @Column(length = 255)
    private String title;

    @Column(name = "equipment_desc", length = 500)
    private String equipmentDesc;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false, length = 16)
    private RateType rateType;

    @Column(name = "base_rate")
    private Long baseRate;

    @Column(name = "rate_early")
    private Long rateEarly;

    @Column(name = "rate_lunch")
    private Long rateLunch;

    @Column(name = "rate_evening")
    private Long rateEvening;

    @Column(name = "rate_night")
    private Long rateNight;

    @Column(name = "rate_overnight")
    private Long rateOvernight;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "file_key", length = 255)
    private String fileKey;

    @Column(columnDefinition = "text")
    private String memo;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Contract create(Long supplierCompanyId, Long createdBy) {
        Contract c = new Contract();
        c.supplierCompanyId = supplierCompanyId;
        c.createdBy = createdBy;
        c.createdAt = LocalDateTime.now();
        c.updatedAt = c.createdAt;
        return c;
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
