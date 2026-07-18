package com.skep.onboarding;

import com.skep.document.OwnerType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 기통과 소급 + 구두승인 레코드(§3.8). 현장×자원 단위.
 * 확정(APPROVED/VERBAL) 시 서비스가 기존 ResourceCheck 승인행을 자동 생성해 게이트/readiness 를 무수정 통과시킨다.
 */
@Entity
@Table(name = "site_resource_onboardings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteResourceOnboarding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "site_name", length = 255)
    private String siteName;

    @Column(name = "bp_company_id")
    private Long bpCompanyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "inspection_date")
    private LocalDate inspectionDate;

    @Column(name = "education_date")
    private LocalDate educationDate;

    @Column(name = "health_date")
    private LocalDate healthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OnboardingMode mode;

    @Column(name = "verbal_approver", length = 255)
    private String verbalApprover;

    @Column(name = "verbal_at")
    private LocalDateTime verbalAt;

    @Column(columnDefinition = "text")
    private String memo;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    private static SiteResourceOnboarding base(Long supplierCompanyId, OwnerType ownerType, Long ownerId,
                                               Long siteId, String siteName, Long bpCompanyId,
                                               LocalDate inspectionDate, LocalDate educationDate, LocalDate healthDate,
                                               String memo, Long requestedBy) {
        SiteResourceOnboarding o = new SiteResourceOnboarding();
        o.supplierCompanyId = supplierCompanyId;
        o.ownerType = ownerType;
        o.ownerId = ownerId;
        o.siteId = siteId;
        o.siteName = siteName;
        o.bpCompanyId = bpCompanyId;
        o.inspectionDate = inspectionDate;
        o.educationDate = educationDate;
        o.healthDate = healthDate;
        o.memo = memo;
        o.requestedBy = requestedBy;
        o.requestedAt = LocalDateTime.now();
        return o;
    }

    /** 공급사 신고 → BP 소급 승인 대기. */
    public static SiteResourceOnboarding request(Long supplierCompanyId, OwnerType ownerType, Long ownerId,
                                                 Long siteId, String siteName, Long bpCompanyId,
                                                 LocalDate inspectionDate, LocalDate educationDate, LocalDate healthDate,
                                                 String memo, Long requestedBy) {
        SiteResourceOnboarding o = base(supplierCompanyId, ownerType, ownerId, siteId, siteName, bpCompanyId,
                inspectionDate, educationDate, healthDate, memo, requestedBy);
        o.mode = OnboardingMode.REQUESTED;
        return o;
    }

    /** 공급사 구두승인 기록 — 즉시 확정. */
    public static SiteResourceOnboarding verbal(Long supplierCompanyId, OwnerType ownerType, Long ownerId,
                                                Long siteId, String siteName, Long bpCompanyId,
                                                LocalDate inspectionDate, LocalDate educationDate, LocalDate healthDate,
                                                String verbalApprover, String memo, Long requestedBy) {
        SiteResourceOnboarding o = base(supplierCompanyId, ownerType, ownerId, siteId, siteName, bpCompanyId,
                inspectionDate, educationDate, healthDate, memo, requestedBy);
        o.mode = OnboardingMode.VERBAL;
        o.verbalApprover = verbalApprover;
        o.verbalAt = LocalDateTime.now();
        return o;
    }

    /** BP 소급 승인. */
    public void approve(Long approverUserId) {
        this.mode = OnboardingMode.APPROVED;
        this.approvedBy = approverUserId;
        this.approvedAt = LocalDateTime.now();
    }
}
