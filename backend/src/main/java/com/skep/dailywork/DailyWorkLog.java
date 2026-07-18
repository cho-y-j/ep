package com.skep.dailywork;

import com.skep.contract.RateType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 일일 작업확인서(§3.6.3) — 하루 1건. 장비업체 전표를 디지털화.
 * BP 인앱 서명(sign_image) 또는 종이 전표 사진 갈음(slip_photo_key, 단독모드).
 * OT 5분류(조출·점심·연장·야간·철야)는 시간수. 월간 원장·작업자 내역은 이 레코드의 뷰.
 */
@Entity
@Table(name = "daily_work_logs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyWorkLog {

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

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "person_id")
    private Long personId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "work_content", length = 500)
    private String workContent;

    @Column(name = "work_location", length = 255)
    private String workLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false, length = 16)
    private RateType rateType;

    @Column(name = "ot_early", nullable = false)
    private BigDecimal otEarly = BigDecimal.ZERO;

    @Column(name = "ot_lunch", nullable = false)
    private BigDecimal otLunch = BigDecimal.ZERO;

    @Column(name = "ot_evening", nullable = false)
    private BigDecimal otEvening = BigDecimal.ZERO;

    @Column(name = "ot_night", nullable = false)
    private BigDecimal otNight = BigDecimal.ZERO;

    @Column(name = "ot_overnight", nullable = false)
    private BigDecimal otOvernight = BigDecimal.ZERO;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(columnDefinition = "text")
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "sign_status", nullable = false, length = 16)
    private WorkLogSignStatus signStatus = WorkLogSignStatus.UNSIGNED;

    @Column(name = "bp_signed_by")
    private Long bpSignedBy;

    @Column(name = "bp_signed_at")
    private LocalDateTime bpSignedAt;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "sign_image", columnDefinition = "bytea")
    private byte[] signImage;

    @Column(name = "slip_photo_key", length = 255)
    private String slipPhotoKey;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static DailyWorkLog create(Long supplierCompanyId, Long createdBy) {
        DailyWorkLog l = new DailyWorkLog();
        l.supplierCompanyId = supplierCompanyId;
        l.createdBy = createdBy;
        l.createdAt = LocalDateTime.now();
        l.updatedAt = l.createdAt;
        return l;
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
