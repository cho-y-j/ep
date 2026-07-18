package com.skep.settlement;

import com.skep.contract.Contract;
import com.skep.dailywork.DailyWorkLog;
import com.skep.dailywork.WorkLogSignStatus;
import com.skep.settlement.dto.SettlementDtos.OtBreakdown;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 정산 OT 5분류 확장(§3.6.3) — SettlementCalculator 본문 무수정, 별도 계산 계층.
 * 자원의 일일 확인서(daily_work_logs)에서 분류별 시간 합계 × 계약 분류별 단가.
 *
 * <p>인정 규칙(판단·보고):
 * <ul>
 *   <li>서명 SIGNED(BP 인앱 서명) 또는 PHOTO(전표 사진 갈음, 단독모드) 만 인정 — UNSIGNED(초안) 제외.</li>
 *   <li>contract_id 연결 건만 — 미연결/계약 없음/단가 없음은 0 처리.</li>
 * </ul>
 * 결과가 없으면(인정 로그 0) null 반환.
 */
public final class OtBreakdownCalculator {

    private OtBreakdownCalculator() {}

    /**
     * @param logs         한 자원의 일일 확인서(기간 필터는 호출측에서 선적용).
     * @param contractsById 로그가 참조하는 계약 맵(단가 원천).
     * @return 인정 로그가 있으면 5분류 breakdown, 없으면 null.
     */
    public static OtBreakdown compute(List<DailyWorkLog> logs, Map<Long, Contract> contractsById) {
        if (logs == null || logs.isEmpty()) return null;
        BigDecimal earlyH = BigDecimal.ZERO, lunchH = BigDecimal.ZERO, eveningH = BigDecimal.ZERO,
                nightH = BigDecimal.ZERO, overnightH = BigDecimal.ZERO;
        long earlyA = 0, lunchA = 0, eveningA = 0, nightA = 0, overnightA = 0;
        int count = 0;
        Long contractId = null;

        for (DailyWorkLog l : logs) {
            // 서명 인정(SIGNED/PHOTO)만, 계약 연결 건만.
            if (l.getSignStatus() != WorkLogSignStatus.SIGNED && l.getSignStatus() != WorkLogSignStatus.PHOTO) continue;
            if (l.getContractId() == null) continue;
            Contract c = contractsById.get(l.getContractId());
            if (c == null) continue;

            if (contractId == null) contractId = c.getId();
            count++;
            earlyH = earlyH.add(nz(l.getOtEarly()));
            lunchH = lunchH.add(nz(l.getOtLunch()));
            eveningH = eveningH.add(nz(l.getOtEvening()));
            nightH = nightH.add(nz(l.getOtNight()));
            overnightH = overnightH.add(nz(l.getOtOvernight()));
            earlyA += money(l.getOtEarly(), c.getRateEarly());
            lunchA += money(l.getOtLunch(), c.getRateLunch());
            eveningA += money(l.getOtEvening(), c.getRateEvening());
            nightA += money(l.getOtNight(), c.getRateNight());
            overnightA += money(l.getOtOvernight(), c.getRateOvernight());
        }

        if (count == 0) return null;
        long total = earlyA + lunchA + eveningA + nightA + overnightA;
        return new OtBreakdown(
                contractId,
                earlyH, earlyA, lunchH, lunchA, eveningH, eveningA,
                nightH, nightA, overnightH, overnightA,
                total, count);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** 시간 × 단가(원). 단가/시간 없으면 0. 반올림 HALF_UP. */
    private static long money(BigDecimal hours, Long rate) {
        if (rate == null || hours == null || hours.signum() == 0) return 0L;
        return hours.multiply(BigDecimal.valueOf(rate)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
