package com.skep.quotation.dispatch;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 선정 통보 받은 공급사가 차량과 함께 보낼 인원(운전수/오퍼레이터/작업자) + 인당 단가.
 * UNIQUE (quotation_request_id, person_id) — 같은 견적에 같은 인원 중복 send 차단.
 * 장비 배차(DispatchedEquipment)와 동일 패턴.
 */
@Entity
@Table(name = "quotation_dispatched_persons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DispatchedPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quotation_request_id", nullable = false)
    private Long quotationRequestId;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    /** V77 4-a: 자원 실소유 자식 공급사 id. 부모가 자식 인원을 자기 명의로 발송했을 때만 채워짐. 본인 자원이면 NULL. */
    @Column(name = "sub_supplier_company_id")
    private Long subSupplierCompanyId;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "daily_price")
    private Long dailyPrice;

    @Column(name = "ot_daily_price")
    private Long otDailyPrice;

    @Column(name = "monthly_price")
    private Long monthlyPrice;

    @Column(name = "ot_monthly_price")
    private Long otMonthlyPrice;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** 정산용 근무일수 — 투입 관리에서 입력(출역 데이터 미연동). NULL=미입력이면 정산 금액 미산출. */
    @Column(name = "settlement_work_days")
    private Integer settlementWorkDays;

    /** 정산용 추가근무(OT) 일수. */
    @Column(name = "settlement_ot_days")
    private Integer settlementOtDays;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "sent_by")
    private Long sentBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private DispatchedPerson(Long quotationRequestId, Long supplierCompanyId, Long subSupplierCompanyId, Long personId,
                             Long dailyPrice, Long otDailyPrice, Long monthlyPrice, Long otMonthlyPrice, String notes, Long sentBy) {
        this.quotationRequestId = quotationRequestId;
        this.supplierCompanyId = supplierCompanyId;
        this.subSupplierCompanyId = subSupplierCompanyId;
        this.personId = personId;
        this.dailyPrice = dailyPrice;
        this.otDailyPrice = otDailyPrice;
        this.monthlyPrice = monthlyPrice;
        this.otMonthlyPrice = otMonthlyPrice;
        this.notes = notes;
        this.sentBy = sentBy;
    }

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        this.sentAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 투입 관리에서 정산용 근무일수·OT일수 입력/수정. */
    public void applySettlementQuantity(Integer workDays, Integer otDays) {
        this.settlementWorkDays = workDays;
        this.settlementOtDays = otDays;
    }
}
