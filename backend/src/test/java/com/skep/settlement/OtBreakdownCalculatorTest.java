package com.skep.settlement;

import com.skep.contract.Contract;
import com.skep.contract.RateType;
import com.skep.dailywork.DailyWorkLog;
import com.skep.dailywork.WorkLogSignStatus;
import com.skep.settlement.dto.SettlementDtos.OtBreakdown;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 정산 OT 5분류(§3.6.3) 계산 회귀 방어 — SettlementCalculator 무수정, 별도 계층.
 * 인정 규칙: 서명 SIGNED/PHOTO 만, contract_id 연결 건만. 계약/단가 없음 → 0/null.
 */
class OtBreakdownCalculatorTest {

    private static Contract contract(long id, Long early, Long lunch, Long evening, Long night, Long overnight) {
        Contract c = Contract.create(1L, 1L);
        c.setId(id);
        c.setRateType(RateType.DAILY);
        c.setRateEarly(early);
        c.setRateLunch(lunch);
        c.setRateEvening(evening);
        c.setRateNight(night);
        c.setRateOvernight(overnight);
        return c;
    }

    private static DailyWorkLog log(WorkLogSignStatus sign, Long contractId,
                                    String early, String lunch, String evening, String night, String overnight) {
        DailyWorkLog l = DailyWorkLog.create(1L, 1L);
        l.setSignStatus(sign);
        l.setContractId(contractId);
        l.setOtEarly(new BigDecimal(early));
        l.setOtLunch(new BigDecimal(lunch));
        l.setOtEvening(new BigDecimal(evening));
        l.setOtNight(new BigDecimal(night));
        l.setOtOvernight(new BigDecimal(overnight));
        return l;
    }

    /** 케이스1: 혼합 분류 — SIGNED + PHOTO 두 건, 5분류에 걸쳐 시간·금액 합산. */
    @Test
    void mixedCategoriesSumHoursAndAmounts() {
        Map<Long, Contract> contracts = Map.of(10L, contract(10L, 30000L, 20000L, 40000L, 50000L, 80000L));
        // A(SIGNED): 조출 2.0·연장 1.5 / B(PHOTO): 점심 1.0·야간 2.0·철야 0.5
        List<DailyWorkLog> logs = List.of(
                log(WorkLogSignStatus.SIGNED, 10L, "2.0", "0", "1.5", "0", "0"),
                log(WorkLogSignStatus.PHOTO, 10L, "0", "1.0", "0", "2.0", "0.5"));

        OtBreakdown b = OtBreakdownCalculator.compute(logs, contracts);

        assertEquals(2, b.logCount());
        assertEquals(10L, b.contractId());
        assertEquals(0, b.earlyHours().compareTo(new BigDecimal("2.0")));
        assertEquals(0, b.lunchHours().compareTo(new BigDecimal("1.0")));
        assertEquals(0, b.eveningHours().compareTo(new BigDecimal("1.5")));
        assertEquals(0, b.nightHours().compareTo(new BigDecimal("2.0")));
        assertEquals(0, b.overnightHours().compareTo(new BigDecimal("0.5")));
        assertEquals(60000L, b.earlyAmount());     // 2.0 × 30000
        assertEquals(20000L, b.lunchAmount());     // 1.0 × 20000
        assertEquals(60000L, b.eveningAmount());   // 1.5 × 40000
        assertEquals(100000L, b.nightAmount());    // 2.0 × 50000
        assertEquals(40000L, b.overnightAmount()); // 0.5 × 80000
        assertEquals(280000L, b.totalOtAmount());
    }

    /** 케이스2: 미서명 제외 — UNSIGNED 는 집계에서 빠지고 SIGNED 만 반영. */
    @Test
    void unsignedLogsExcluded() {
        Map<Long, Contract> contracts = Map.of(10L, contract(10L, 30000L, 20000L, 40000L, 50000L, 80000L));
        List<DailyWorkLog> logs = List.of(
                log(WorkLogSignStatus.SIGNED, 10L, "3.0", "0", "0", "0", "0"),
                log(WorkLogSignStatus.UNSIGNED, 10L, "5.0", "0", "0", "0", "0")); // 제외

        OtBreakdown b = OtBreakdownCalculator.compute(logs, contracts);

        assertEquals(1, b.logCount());
        assertEquals(0, b.earlyHours().compareTo(new BigDecimal("3.0")));
        assertEquals(90000L, b.earlyAmount());   // 3.0 × 30000 (UNSIGNED 5.0 미포함)
        assertEquals(90000L, b.totalOtAmount());
    }

    /** 케이스3: 계약 없음 — contract_id 미연결 / 맵에 없는 계약이면 인정 로그 0 → null. */
    @Test
    void noContractYieldsNull() {
        Map<Long, Contract> contracts = Map.of(10L, contract(10L, 30000L, 20000L, 40000L, 50000L, 80000L));
        List<DailyWorkLog> logs = List.of(
                log(WorkLogSignStatus.SIGNED, null, "3.0", "0", "0", "0", "0"),  // contract_id 없음
                log(WorkLogSignStatus.SIGNED, 99L, "3.0", "0", "0", "0", "0"));  // 맵에 없는 계약

        assertNull(OtBreakdownCalculator.compute(logs, contracts));
    }

    /** 케이스4: 단가 없음 → 0 처리 + 비정수 시간 반올림(HALF_UP). */
    @Test
    void nullRateIsZeroAndHoursRoundHalfUp() {
        // 조출 단가 null → 금액 0(시간은 집계). 점심 단가 33333 × 1.5 = 49999.5 → 50000.
        Map<Long, Contract> contracts = Map.of(10L, contract(10L, null, 33333L, null, null, null));
        List<DailyWorkLog> logs = List.of(
                log(WorkLogSignStatus.SIGNED, 10L, "2.0", "1.5", "0", "0", "0"));

        OtBreakdown b = OtBreakdownCalculator.compute(logs, contracts);

        assertEquals(1, b.logCount());
        assertEquals(0, b.earlyHours().compareTo(new BigDecimal("2.0")));
        assertEquals(0L, b.earlyAmount());       // 단가 null → 0
        assertEquals(50000L, b.lunchAmount());   // 1.5 × 33333 = 49999.5 → 50000
        assertEquals(50000L, b.totalOtAmount());
    }
}
