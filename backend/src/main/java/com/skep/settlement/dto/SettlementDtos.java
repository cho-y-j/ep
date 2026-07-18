package com.skep.settlement.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 소유자별 투입 정산 요약. 배차 행 기반. 금액 = 근무일수 기반(월대÷25×근무일수 + OT, 일대×근무일수 + OT).
 * 근무일수·OT일수는 투입 관리에서 입력(미입력이면 amount=null). 수수료 미표기.
 * work_period(견적 계약기간)은 표시용으로 유지. JSON 전역 SNAKE_CASE — 필드는 camelCase record.
 */
public class SettlementDtos {

    /** 배차 1행 = 정산 1건. amount = SettlementCalculator 규칙(근무일수 기반), 근무일수 미입력이면 null. */
    public record SettlementItem(
            String resourceType,          // EQUIPMENT / PERSON
            Long dispatchId,
            Long resourceId,              // 장비/인원 id (단가 응답 등 미지정이면 null)
            String resourceLabel,
            Long quotationRequestId,
            Long siteId,
            String siteName,
            Long bpCompanyId,
            String bpCompanyName,
            LocalDate workPeriodStart,    // 견적 계약기간(표시 유지)
            LocalDate workPeriodEnd,
            long periodDays,              // 계약기간 일수(표시)
            Long dailyPrice,
            Long otDailyPrice,
            Long monthlyPrice,
            Long otMonthlyPrice,
            String amountBasis,           // MONTHLY / DAILY / null
            Long amount,                  // 총액 = base + ot (근무일수 미입력이면 null)
            Long supplierCompanyId,       // 대외 명의(부모). 자식 자원을 부모가 발송한 경우 부모 id.
            boolean dispatchedByParent,   // 이 소유자(본인) 자원을 부모가 대신 발송한 행인지
            LocalDateTime sentAt,
            Integer settlementWorkDays,   // 정산용 근무일수(투입관리 입력, null=미입력)
            Integer settlementOtDays,     // 정산용 추가근무 일수
            Long baseAmount,              // 기본 금액(월대÷25×근무일수 or 일대×근무일수)
            Long otAmount,                // OT 금액(OT단가×OT일수)
            Integer siteSettlementDay,    // 현장 정산 기준일(1~31, null=미지정)
            Integer derivedWorkDays,      // 작업확인서(서명완료) 기준 자동 집계 근무일수(인력만, 없으면 null)
            Integer derivedOtDays,        // 자동 집계 OT일수(표시용, 인력 금액엔 미반영)
            String workDaysSource,        // MANUAL(수동입력) / DERIVED(자동집계) / null(둘 다 없음)
            String sourceKind,            // DISPATCH(견적/배차 원천) / DEPLOYMENT(현장 투입요청 원천, §3.2 디커플링)
            OtBreakdown otBreakdown       // 일일 확인서 기반 OT 5분류 내역·금액(§3.6.3). 없으면 null. 기존 amount 엔 미반영.
    ) {}

    /**
     * OT 5분류 내역(§3.6.3) — 일일 확인서(SIGNED/PHOTO) × 계약 분류별 단가.
     * 기존 정산 amount 와 독립된 추가 정보. 계약 미연결/인정 로그 없으면 이 필드 자체가 null.
     */
    public record OtBreakdown(
            Long contractId,
            java.math.BigDecimal earlyHours, Long earlyAmount,
            java.math.BigDecimal lunchHours, Long lunchAmount,
            java.math.BigDecimal eveningHours, Long eveningAmount,
            java.math.BigDecimal nightHours, Long nightAmount,
            java.math.BigDecimal overnightHours, Long overnightAmount,
            Long totalOtAmount,           // 5분류 금액 합계
            int logCount                  // 인정된 일일 확인서 건수
    ) {}

    /** 소유자(본인 or 협력사) 단위 묶음. */
    public record OwnerSettlement(
            Long ownerCompanyId,
            String ownerCompanyName,
            boolean isSelf,
            List<SettlementItem> items,
            long totalAmount,
            int itemCount
    ) {}

    public record SettlementSummaryResponse(
            List<OwnerSettlement> owners,
            long grandTotal
    ) {}
}
